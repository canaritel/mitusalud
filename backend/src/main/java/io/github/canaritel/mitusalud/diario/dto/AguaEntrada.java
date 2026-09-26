package io.github.canaritel.mitusalud.diario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

/**
 * Lo que el cliente envía para registrar agua.
 * observadoEn debe llevar zona horaria, igual que en el peso.
 * mililitros es un entero entre 1 y 5000 por registro; un decimal como 250.5 se rechaza, no se trunca.
 */
public record AguaEntrada(
        @NotNull @PastOrPresent OffsetDateTime observadoEn,
        @NotNull @Positive @Max(5000) Integer mililitros) {
}
