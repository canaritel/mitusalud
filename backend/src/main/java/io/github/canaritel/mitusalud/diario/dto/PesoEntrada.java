package io.github.canaritel.mitusalud.diario.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lo que el cliente envía para registrar un peso.
 * Solo contiene los campos que el cliente puede decidir: el id y la fecha de registro
 * los pone el servidor, así que no hay forma de enviarlos.
 * Las anotaciones son las reglas de validación; si no se cumplen, la API responde 400.
 *
 * observadoEn debe llevar zona horaria, por ejemplo "2026-09-26T08:30:00+02:00" o
 * "2026-09-26T06:30:00Z". Sin ella, "08:30" sería ambiguo y la API responde 400.
 * kilos admite como máximo 2 decimales: 72.355 se rechaza, no se redondea.
 */
public record PesoEntrada(
        @NotNull @PastOrPresent OffsetDateTime observadoEn,
        @NotNull @Positive @DecimalMax(value = "500", inclusive = false) @Digits(integer = 3, fraction = 2) BigDecimal kilos) {
}
