package io.github.canaritel.mitusalud.diario.service;

import io.github.canaritel.mitusalud.diario.dto.PesoEntrada;
import io.github.canaritel.mitusalud.diario.dto.PesoSalida;
import io.github.canaritel.mitusalud.diario.entity.Peso;
import io.github.canaritel.mitusalud.diario.repository.PesoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso del peso. Aquí van las reglas de negocio, nunca en el controller.
 * El service trabaja con entidades por dentro y devuelve DTOs hacia fuera.
 */
@Service
public class PesoService {

    private final PesoRepository pesoRepository;

    // Inyección por constructor: Spring pasa el repository al crear el service.
    // Con un solo constructor no hace falta @Autowired.
    public PesoService(PesoRepository pesoRepository) {
        this.pesoRepository = pesoRepository;
    }

    // @Transactional: o se guarda todo o nada. Guardar un Peso inserta dos filas (observation y peso);
    // si falla la segunda, la transacción deshace también la primera.
    @Transactional
    public PesoSalida registrar(PesoEntrada entrada) {
        // toInstant(): se guarda el instante exacto; la zona horaria con la que llegó no se conserva.
        Peso peso = pesoRepository.save(new Peso(entrada.observadoEn().toInstant(), entrada.kilos()));
        return PesoSalida.de(peso);
    }

    // readOnly avisa a la base de datos de que solo se lee, lo que permite optimizar.
    @Transactional(readOnly = true)
    public List<PesoSalida> listar() {
        return pesoRepository.findAllByOrderByObservadoEnDescIdDesc().stream()
                .map(PesoSalida::de)
                .toList();
    }
}
