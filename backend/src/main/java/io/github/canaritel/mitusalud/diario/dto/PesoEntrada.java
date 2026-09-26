package io.github.canaritel.mitusalud.diario.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que el cliente envía para registrar un peso.
 * Solo contiene los campos que el cliente puede decidir: el id y la fecha de creación
 * los pone el servidor, así que no hay forma de enviarlos.
 * Las anotaciones son las reglas de validación; si no se cumplen, la API responde 400.
 */
public record PesoEntrada(
        @NotNull @PastOrPresent LocalDate fecha,
        @NotNull @Positive @DecimalMax(value = "500", inclusive = false) @Digits(integer = 3, fraction = 2) BigDecimal kilos) {
}
