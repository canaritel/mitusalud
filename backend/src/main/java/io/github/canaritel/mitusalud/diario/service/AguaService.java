package io.github.canaritel.mitusalud.diario.service;

import io.github.canaritel.mitusalud.diario.dto.AguaEntrada;
import io.github.canaritel.mitusalud.diario.dto.AguaSalida;
import io.github.canaritel.mitusalud.diario.entity.Agua;
import io.github.canaritel.mitusalud.diario.repository.AguaRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso del agua. Mismo esquema que PesoService, salvo que registrar es idempotente
 * (docs/MODELO_DATOS.md, pieza 4, versión mínima): la lógica vive aquí mientras solo el agua la use.
 */
@Service
public class AguaService {

    // Nombre de la restricción UNIQUE de la clave (V5): identifica el choque entre dos peticiones.
    private static final String RESTRICCION_CLAVE = "observation_idempotency_key_key";

    private final AguaRepository aguaRepository;
    private final TransactionTemplate transaccion;

    public AguaService(AguaRepository aguaRepository, PlatformTransactionManager gestorTransacciones) {
        this.aguaRepository = aguaRepository;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
    }

    /**
     * Registra un agua una sola vez por clave. Un reintento con la misma clave y los mismos datos
     * devuelve el registro ya guardado; con otros datos, 422.
     *
     * Sin @Transactional a propósito: si el INSERT choca con la clave de otra petición, esa transacción
     * ha fallado y se deshace entera. El registro ganador se busca después, en una transacción nueva.
     */
    public AguaSalida registrar(UUID clave, AguaEntrada entrada) {
        Agua pedida = new Agua(entrada.observadoEn().toInstant(), entrada.mililitros(), clave);

        // 1. Reintento de una operación ya confirmada: la clave existe.
        Optional<Agua> guardada = aguaRepository.findByClaveIdempotencia(clave);
        if (guardada.isPresent()) {
            return respuestaAlReintento(guardada.get(), pedida);
        }

        // 2. Clave nueva: crear y confirmar. El try abarca también el commit, por si el error llegara ahí.
        try {
            return transaccion.execute(estado -> AguaSalida.de(aguaRepository.saveAndFlush(pedida)));
        } catch (DataIntegrityViolationException e) {
            // 3. Otra petición con la misma clave se confirmó mientras tanto y PostgreSQL rechazó este INSERT.
            //    Solo ese choque se trata como reintento; cualquier otro error (un CHECK, por ejemplo) sigue su camino.
            if (!esChoqueDeClave(e)) {
                throw e;
            }
            // Si no aparece como agua, la clave la usó otro tipo de registro.
            Agua ganadora = aguaRepository.findByClaveIdempotencia(clave)
                    .orElseThrow(ClaveIdempotenciaReutilizadaException::new);
            return respuestaAlReintento(ganadora, pedida);
        }
    }

    @Transactional(readOnly = true)
    public List<AguaSalida> listar() {
        return aguaRepository.findAllByOrderByObservadoEnDescIdDesc().stream()
                .map(AguaSalida::de)
                .toList();
    }

    // Mismos datos: se devuelve lo guardado y no se crea nada. Otros datos: 422.
    // observadoEn se compara como instante: 10:00+02:00 y 08:00Z son lo mismo.
    private static AguaSalida respuestaAlReintento(Agua guardada, Agua pedida) {
        boolean mismosDatos = guardada.getObservadoEn().equals(pedida.getObservadoEn())
                && guardada.getMililitros().equals(pedida.getMililitros());
        if (!mismosDatos) {
            throw new ClaveIdempotenciaReutilizadaException();
        }
        return AguaSalida.de(guardada);
    }

    private static boolean esChoqueDeClave(DataIntegrityViolationException e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacion) {
                return RESTRICCION_CLAVE.equals(violacion.getConstraintName());
            }
        }
        return false;
    }
}
