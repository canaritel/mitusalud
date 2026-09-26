package io.github.canaritel.mitusalud.diario.service;

import io.github.canaritel.mitusalud.diario.dto.AguaEntrada;
import io.github.canaritel.mitusalud.diario.dto.AguaSalida;
import io.github.canaritel.mitusalud.diario.entity.Agua;
import io.github.canaritel.mitusalud.diario.repository.AguaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso del agua. Mismo esquema que PesoService: a propósito no se ha creado un service
 * genérico para todos los tipos; se valorará si la repetición llega a estorbar.
 */
@Service
public class AguaService {

    private final AguaRepository aguaRepository;

    public AguaService(AguaRepository aguaRepository) {
        this.aguaRepository = aguaRepository;
    }

    // Guarda dos filas (observation y agua) en una sola transacción: o las dos o ninguna.
    @Transactional
    public AguaSalida registrar(AguaEntrada entrada) {
        Agua agua = aguaRepository.save(new Agua(entrada.observadoEn().toInstant(), entrada.mililitros()));
        return AguaSalida.de(agua);
    }

    @Transactional(readOnly = true)
    public List<AguaSalida> listar() {
        return aguaRepository.findAllByOrderByObservadoEnDescIdDesc().stream()
                .map(AguaSalida::de)
                .toList();
    }
}
