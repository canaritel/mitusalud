package io.github.canaritel.mitusalud.acceso;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;

/**
 * Dónde guarda Spring Security los datos de acceso: en PostgreSQL (tablas de V6), no en memoria.
 * Sin esto, las passkeys y los enlaces se perderían al reiniciar.
 *
 * No depende de la web: también los usa el comando "acceso", que arranca sin servidor web.
 */
@Configuration
class AlmacenesAcceso {

    // Usuarios de WebAuthn: solo el propietario.
    @Bean
    JdbcPublicKeyCredentialUserEntityRepository usuariosWebAuthn(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    // Passkeys registradas.
    @Bean
    JdbcUserCredentialRepository passkeys(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    // Tokens de un solo uso: se borran al usarse, caducan y Spring limpia los caducados.
    @Bean
    JdbcOneTimeTokenService tokensDeUnSoloUso(JdbcOperations jdbc) {
        return new JdbcOneTimeTokenService(jdbc);
    }
}
