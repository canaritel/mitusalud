package io.github.canaritel.mitusalud.acceso;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Reglas propias para gestionar passkeys (/webauthn/register/...). Spring atiende esas rutas en sus propios
 * filtros; este va delante (después del CSRF) y aplica lo que Spring no sabe de mitusalud (L-08 a L-10):
 *
 * - Solo gestiona passkeys el propietario con su passkey, o una sesión de alta vigente mientras el propietario
 *   aún no tiene ninguna passkey (primera configuración o tras recuperar). Eliminar, solo el propietario.
 * - Al completar el alta, la sesión de alta se cierra: a partir de ahí se entra con la passkey.
 * - Eliminar lo hace este filtro, no el de Spring, con CuentaPropietario.eliminarPasskey: comprobar que no es
 *   la última, borrarla y cerrar todas las sesiones ocurren juntos, en una transacción.
 */
class FiltroPasskeys extends OncePerRequestFilter {

    private static final RequestMatcher GESTIONAR = PathPatternRequestMatcher.withDefaults().matcher("/webauthn/register/**");
    private static final RequestMatcher REGISTRAR = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/webauthn/register");
    private static final PathPatternRequestMatcher ELIMINAR = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.DELETE, "/webauthn/register/{id}");

    private final CuentaPropietario cuenta;

    FiltroPasskeys(CuentaPropietario cuenta) {
        this.cuenta = cuenta;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (!GESTIONAR.matches(peticion)) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        Authentication quien = SecurityContextHolder.getContext().getAuthentication();
        boolean propietario = cuenta.esSesionConPasskeyDelPropietario(quien);
        boolean alta = quien instanceof SesionDeAlta sesion && sesion.vigente(Instant.now()) && cuenta.passkeys() == 0;
        boolean eliminar = ELIMINAR.matches(peticion);

        if (!propietario && !(alta && !eliminar)) {
            respuesta.sendError(quien == null ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (eliminar) {
            String id = ELIMINAR.matcher(peticion).getVariables().get("id");
            switch (cuenta.eliminarPasskey(id)) {
                case HECHA -> {
                    // Las sesiones ya se borraron en la transacción; se invalida también el objeto de esta petición.
                    cerrarSesionActual(peticion);
                    respuesta.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
                case ES_LA_ULTIMA -> respuesta.sendError(HttpServletResponse.SC_CONFLICT, "No se puede eliminar la última passkey");
                case NO_EXISTE -> respuesta.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }

        cadena.doFilter(peticion, respuesta);

        if (alta && REGISTRAR.matches(peticion) && respuesta.getStatus() < 300) {
            cerrarSesionActual(peticion);
        }
    }

    // Con Spring Session, invalidar la sesión la borra de PostgreSQL en ese momento.
    private static void cerrarSesionActual(HttpServletRequest peticion) {
        HttpSession sesion = peticion.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
