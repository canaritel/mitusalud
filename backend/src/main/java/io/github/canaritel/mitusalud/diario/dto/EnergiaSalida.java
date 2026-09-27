package io.github.canaritel.mitusalud.diario.dto;

import io.github.canaritel.mitusalud.diario.entity.Energia;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que la API devuelve de un registro de energía. observadoEn sale en UTC; nota es null si no hay.
 */
public record EnergiaSalida(UUID id, Instant observadoEn, Integer nivel, String nota) {

    public static EnergiaSalida de(Energia energia) {
        return new EnergiaSalida(energia.getId(), energia.getObservadoEn(), energia.getNivel(), energia.getNota());
    }
}
