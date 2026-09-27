package io.github.canaritel.mitusalud.diario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Lo que el cliente envía para registrar su energía.
 * nivel es un entero de 1 a 5; un decimal como 2.5 se rechaza, no se trunca.
 * nota es opcional: hasta 500 unidades UTF-16 (lo que cuenta String.length(); algunos emojis ocupan 2).
 *
 * El @Pattern rechaza dos caracteres que JSON deja escribir pero que no son texto:
 * el nulo (U+0000), que PostgreSQL no admite (sería un error 500), y una mitad suelta de emoji
 * (\p{Cs}), que el driver cambiaría por "?" sin avisar. Un emoji completo sí pasa.
 */
public record EnergiaEntrada(
        @NotNull @PastOrPresent OffsetDateTime observadoEn,
        @NotNull @Min(1) @Max(5) Integer nivel,
        @Size(max = 500)
        @Pattern(regexp = "[^\\x00\\p{Cs}]*", message = "{validacion.texto.caracteresNoValidos}")
        String nota) {
}
