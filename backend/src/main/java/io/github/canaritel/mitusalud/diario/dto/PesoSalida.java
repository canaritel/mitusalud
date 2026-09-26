package io.github.canaritel.mitusalud.diario.dto;

import io.github.canaritel.mitusalud.diario.entity.Peso;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que la API devuelve de un peso.
 * Separarlo de la entidad permite cambiar la tabla sin romper la API sin darse cuenta.
 */
public record PesoSalida(Long id, LocalDate fecha, BigDecimal kilos) {

    // Conversión a mano, a la vista y sin librerías de mapeo.
    public static PesoSalida de(Peso peso) {
        return new PesoSalida(peso.getId(), peso.getFecha(), peso.getKilos());
    }
}
