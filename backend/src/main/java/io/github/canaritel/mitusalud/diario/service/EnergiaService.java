package io.github.canaritel.mitusalud.diario.service;

import io.github.canaritel.mitusalud.diario.dto.EnergiaEntrada;
import io.github.canaritel.mitusalud.diario.dto.EnergiaSalida;
import io.github.canaritel.mitusalud.diario.entity.Energia;
import io.github.canaritel.mitusalud.diario.repository.EnergiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de la energía. Mismo esquema que PesoService y AguaService: se repite la estructura,
 * no la lógica. Cuando llegue lógica común a todos los tipos (editar, borrar...), irá en un solo
 * sitio en vez de copiarse (docs/MODELO_DATOS.md, condiciones futuras).
 */
@Service
public class EnergiaService {

    private final EnergiaRepository energiaRepository;

    public EnergiaService(EnergiaRepository energiaRepository) {
        this.energiaRepository = energiaRepository;
    }

    // Guarda dos filas (observation y energia) en una sola transacción: o las dos o ninguna.
    @Transactional
    public EnergiaSalida registrar(EnergiaEntrada entrada) {
        Energia energia = energiaRepository.save(
                new Energia(entrada.observadoEn().toInstant(), entrada.nivel(), entrada.nota()));
        return EnergiaSalida.de(energia);
    }

    @Transactional(readOnly = true)
    public List<EnergiaSalida> listar() {
        return energiaRepository.findAllByOrderByObservadoEnDescIdDesc().stream()
                .map(EnergiaSalida::de)
                .toList();
    }
}
