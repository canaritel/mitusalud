package io.github.canaritel.mitusalud.diario.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * La clave de idempotencia ya se usó con otros datos: responde 422 y no se crea nada.
 * Un registro nuevo necesita una clave nueva; un reintento repite la clave y los datos.
 *
 * Al heredar de ErrorResponseException, ManejadorErroresApi la convierte en ProblemDetail
 * con título y detalle de messages*.properties, en el idioma de la petición.
 */
public class ClaveIdempotenciaReutilizadaException extends ErrorResponseException {

    public ClaveIdempotenciaReutilizadaException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
