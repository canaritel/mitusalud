package io.github.canaritel.mitusalud.comun;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Da formato a los errores de toda la API.
 *
 * Hereda de ResponseEntityExceptionHandler, que ya convierte los errores habituales de Spring
 * (JSON mal formado, método no permitido...) en ProblemDetail (RFC 9457).
 * Solo se cambia el caso de validación, para indicar qué campos fallan.
 *
 * Ejemplo de respuesta 400:
 * {
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "Hay campos con valores no válidos.",
 *   "instance": "/api/v1/pesos",
 *   "errores": { "kilos": "debe ser mayor que 0" }
 * }
 */
@RestControllerAdvice
public class ManejadorErroresApi extends ResponseEntityExceptionHandler {

    // Spring lanza MethodArgumentNotValidException cuando falla un @Valid en un controller.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problema = ex.getBody();
        problema.setDetail("Hay campos con valores no válidos.");

        // Un mensaje por campo: si un campo incumple varias reglas, basta con la primera.
        // LinkedHashMap conserva el orden en que Spring detectó los errores.
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problema.setProperty("errores", errores);

        return handleExceptionInternal(ex, problema, headers, status, request);
    }
}
