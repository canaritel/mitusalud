package io.github.canaritel.mitusalud.acceso;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sesión abierta con el enlace de un solo uso. Solo sirve para registrar una passkey
 * (la primera, o una nueva tras recuperar) y no da ningún acceso al diario.
 *
 * Caduca a los 10 minutos aunque se siga usando: la hora límite viaja dentro de la propia sesión.
 * Al completar el alta se cierra, y a partir de ahí se entra con la passkey (FiltroPasskeys).
 */
public class SesionDeAlta extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    static final Duration DURACION = Duration.ofMinutes(10);

    private final String nombre;
    private final Instant caducaEn;

    public SesionDeAlta(String nombre, Instant caducaEn) {
        super(List.of()); // sin permisos: las reglas de acceso la reconocen por su tipo
        this.nombre = nombre;
        this.caducaEn = caducaEn;
        setAuthenticated(true);
    }

    public boolean vigente(Instant ahora) {
        return ahora.isBefore(caducaEn);
    }

    @Override
    public Object getPrincipal() {
        return nombre;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
