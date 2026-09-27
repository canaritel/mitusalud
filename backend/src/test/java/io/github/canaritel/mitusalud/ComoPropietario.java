package io.github.canaritel.mitusalud;

import io.github.canaritel.mitusalud.acceso.CuentaPropietario;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Para los tests de la API que pasan por la seguridad real: la petición llega como una sesión abierta
 * con passkey del propietario y con su token CSRF. Cómo se entra de verdad lo prueba acceso/AccesoIT.
 */
public final class ComoPropietario {

    private ComoPropietario() {
    }

    // Crea el propietario si hace falta (en los tests con @Transactional, se deshace al terminar).
    public static RequestPostProcessor propietario(CuentaPropietario cuenta, PublicKeyCredentialUserEntityRepository usuarios) {
        cuenta.crearSiNoExiste();
        WebAuthnAuthentication conPasskey = new WebAuthnAuthentication(usuarios.findByUsername(CuentaPropietario.NOMBRE), List.of());
        return peticion -> csrf().postProcessRequest(authentication(conPasskey).postProcessRequest(peticion));
    }
}
