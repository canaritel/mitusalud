package io.github.canaritel.mitusalud.acceso;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ott.InvalidOneTimeTokenException;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.core.Authentication;

import java.time.Instant;

/**
 * Entrar con el enlace de un solo uso. Sustituye al de Spring, que abriría una sesión completa:
 * aquí el token solo abre una SesionDeAlta.
 */
class ProveedorSesionDeAlta implements AuthenticationProvider {

    private final OneTimeTokenService tokens;

    ProveedorSesionDeAlta(OneTimeTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public Authentication authenticate(Authentication peticion) {
        // consume() borra el token: un segundo uso ya no lo encuentra. Si no existe o caducó, devuelve null.
        OneTimeToken token = tokens.consume((OneTimeTokenAuthenticationToken) peticion);
        if (token == null) {
            throw new InvalidOneTimeTokenException("Enlace no válido o caducado");
        }
        return new SesionDeAlta(token.getUsername(), Instant.now().plus(SesionDeAlta.DURACION));
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return OneTimeTokenAuthenticationToken.class.isAssignableFrom(tipo);
    }
}
