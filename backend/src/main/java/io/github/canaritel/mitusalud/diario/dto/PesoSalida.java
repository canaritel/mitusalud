package io.github.canaritel.mitusalud.diario.dto;

import io.github.canaritel.mitusalud.diario.entity.Peso;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que la API devuelve de un peso.
 * Separarlo de la entidad permite cambiar las tablas sin romper la API sin darse cuenta.
 *
 * observadoEn sale siempre en UTC ("2026-09-26T06:30:00Z"): la base de datos guarda el instante,
 * no la zona horaria con la que se envió. Mostrarlo en hora local es trabajo de la interfaz.
 */
public record PesoSalida(UUID id, Instant observadoEn, BigDecimal kilos) {

    // Conversión a mano, a la vista y sin librerías de mapeo.
    public static PesoSalida de(Peso peso) {
        return new PesoSalida(peso.getId(), peso.getObservadoEn(), peso.getKilos());
    }
}
