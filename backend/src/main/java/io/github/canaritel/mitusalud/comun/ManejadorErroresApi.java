package io.github.canaritel.mitusalud.comun;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Da formato a los errores de toda la API.
 *
 * Hereda de ResponseEntityExceptionHandler, que ya convierte los errores habituales de Spring
 * (JSON mal formado, método no permitido...) en ProblemDetail (RFC 9457).
 * Solo se cambia el caso de validación, para indicar qué campos fallan.
 * Los textos se traducen según la cabecera Accept-Language (ver messages*.properties).
 *
 * Ejemplo de respuesta 400:
 * {
 *   "title": "Petición no válida",
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

        // Título y detalle salen de messages*.properties, en el idioma de la petición.
        ProblemDetail problema = ex.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());

        // Un mensaje por campo: si un campo incumple varias reglas, basta con la primera.
        // TreeMap ordena los campos alfabéticamente: el validador no garantiza ningún orden
        // y así la misma petición produce siempre la misma respuesta.
        Map<String, String> errores = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problema.setProperty("errores", errores);

        return handleExceptionInternal(ex, problema, headers, status, request);
    }
}
