package io.github.canaritel.mitusalud.acceso;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * El propietario de la instalación y lo que se puede hacer con su acceso
 * (docs/MODELO_DATOS.md, pieza 3; backlog L-08 a L-11).
 *
 * Solo hay un usuario, "propietario". Se identifica por el identificador interno de sus passkeys
 * (user handle), guardado en owner_account, nunca por el nombre ni el correo.
 */
@Component
public class CuentaPropietario {

    public static final String NOMBRE = "propietario";

    // Vigencia del enlace de alta o de recuperación.
    static final Duration VIGENCIA_ENLACE = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final PublicKeyCredentialUserEntityRepository usuarios;
    private final OneTimeTokenService tokens;

    public CuentaPropietario(JdbcTemplate jdbc, PublicKeyCredentialUserEntityRepository usuarios, OneTimeTokenService tokens) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.tokens = tokens;
    }

    /**
     * Primera configuración: crea el propietario si no existe y devuelve el token del enlace para registrar
     * su primera passkey. Si ya tiene passkeys se niega: para eso está la recuperación, que además revoca.
     */
    @Transactional
    public String primerAcceso() {
        if (identificador().isPresent() && passkeys() > 0) {
            throw new IllegalStateException("El propietario ya tiene passkeys. Si no puede entrar, usa \"acceso recuperar\".");
        }
        crearSiNoExiste();
        return emitirEnlace();
    }

    // Crea el usuario de WebAuthn del propietario y su fila de owner_account, si aún no existen.
    @Transactional
    public void crearSiNoExiste() {
        if (identificador().isPresent()) {
            return;
        }
        // El identificador es aleatorio: no revela nada del propietario.
        PublicKeyCredentialUserEntity usuario = ImmutablePublicKeyCredentialUserEntity.builder()
                .name(NOMBRE).id(Bytes.random()).displayName("Propietario").build();
        usuarios.save(usuario);
        jdbc.update("INSERT INTO owner_account (user_handle) VALUES (?)", usuario.getId().toBase64UrlString());
    }

    /**
     * Recuperación cuando ninguna passkey funciona. En una sola transacción: revoca todas las passkeys,
     * invalida los enlaces anteriores y cierra todas las sesiones. El propietario sigue siendo el mismo.
     * Todo ocurre al ejecutar el comando: el dispositivo perdido queda fuera aunque el enlace nunca se use.
     */
    @Transactional
    public String recuperar() {
        String id = identificador().orElseThrow(() ->
                new IllegalStateException("Todavía no hay propietario. Usa \"acceso primero\"."));
        jdbc.update("DELETE FROM user_credentials WHERE user_entity_user_id = ?", id);
        return emitirEnlace(); // también cierra todas las sesiones
    }

    // Resultado de intentar eliminar una passkey.
    public enum Eliminacion { HECHA, ES_LA_ULTIMA, NO_EXISTE }

    /**
     * Elimina una passkey del propietario y cierra todas las sesiones, en una sola transacción: si algo falla,
     * no se borra nada. La fila del propietario se bloquea (FOR UPDATE) para que dos eliminaciones simultáneas
     * no lo dejen sin passkeys: la segunda espera a la primera y, cuando cuenta, ya solo queda una.
     */
    @Transactional
    public Eliminacion eliminarPasskey(String idCredencial) {
        String propietario = jdbc.queryForObject("SELECT user_handle FROM owner_account FOR UPDATE", String.class);
        if (passkeys() <= 1) {
            return Eliminacion.ES_LA_ULTIMA;
        }
        int borradas = jdbc.update("DELETE FROM user_credentials WHERE credential_id = ? AND user_entity_user_id = ?",
                idCredencial, propietario);
        if (borradas == 0) {
            return Eliminacion.NO_EXISTE;
        }
        cerrarTodasLasSesiones();
        return Eliminacion.HECHA;
    }

    // Identificador interno del propietario, si ya existe.
    public Optional<String> identificador() {
        return jdbc.query("SELECT user_handle FROM owner_account", (fila, numero) -> fila.getString(1))
                .stream().findFirst();
    }

    // true solo si es una sesión abierta con passkey y la passkey es del propietario.
    // Una sesión de alta (enlace) no cuenta: no da acceso al diario.
    public boolean esSesionConPasskeyDelPropietario(Authentication autenticacion) {
        return autenticacion instanceof WebAuthnAuthentication conPasskey
                && conPasskey.isAuthenticated()
                && identificador().map(id -> id.equals(conPasskey.getPrincipal().getId().toBase64UrlString())).orElse(false);
    }

    public int passkeys() {
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM user_credentials c JOIN owner_account o ON o.user_handle = c.user_entity_user_id
                """, Integer.class);
        return total == null ? 0 : total;
    }

    // Solo hay un propietario: cerrar todas las sesiones es borrar todas las filas de Spring Session.
    public void cerrarTodasLasSesiones() {
        jdbc.update("DELETE FROM spring_session");
    }

    // Un enlace nuevo invalida los anteriores y cierra todas las sesiones, incluidas las de alta que esos
    // enlaces ya abrieron: nunca hay más de un alta en marcha.
    private String emitirEnlace() {
        jdbc.update("DELETE FROM one_time_tokens WHERE username = ?", NOMBRE);
        cerrarTodasLasSesiones();
        return tokens.generate(new GenerateOneTimeTokenRequest(NOMBRE, VIGENCIA_ENLACE)).getTokenValue();
    }
}
