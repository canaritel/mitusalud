package io.github.canaritel.mitusalud.diario.dto;

import io.github.canaritel.mitusalud.diario.entity.Agua;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que la API devuelve de un registro de agua. observadoEn sale en UTC, como en el peso.
 */
public record AguaSalida(UUID id, Instant observadoEn, Integer mililitros) {

    public static AguaSalida de(Agua agua) {
        return new AguaSalida(agua.getId(), agua.getObservadoEn(), agua.getMililitros());
    }
}
