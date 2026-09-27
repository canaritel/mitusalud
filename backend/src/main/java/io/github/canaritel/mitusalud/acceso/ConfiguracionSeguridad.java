package io.github.canaritel.mitusalud.acceso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

import java.util.List;
import java.util.Set;

/**
 * Quién puede entrar y a qué (docs/STACK.md, "Identidad"; backlog L-08 a L-10):
 * - Los datos (/api/**) solo con una sesión abierta con passkey del propietario.
 * - El enlace de un solo uso abre una SesionDeAlta, que solo sirve para registrar una passkey.
 * - Passkeys con verificación del usuario obligatoria; sesión en cookie y protección CSRF.
 *
 * Solo con servidor web: el comando "acceso" arranca sin él y no carga esta configuración.
 */
@Configuration
@ConditionalOnWebApplication
class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, CuentaPropietario cuenta, OneTimeTokenService tokens) {
        AuthorizationManager<RequestAuthorizationContext> propietario = (autenticacion, contexto) ->
                new AuthorizationDecision(cuenta.esSesionConPasskeyDelPropietario(autenticacion.get()));

        http
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers("/api/**").access(propietario)
                        // Salud, páginas de error y documentación de la API: no contienen datos.
                        .requestMatchers("/actuator/health", "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Entrar, con passkey o con el enlace: las páginas y los envíos los atiende Spring.
                        .requestMatchers("/login/**", "/webauthn/authenticate/**", "/default-ui.css").permitAll()
                        // Gestionar passkeys: sus reglas están en FiltroPasskeys, que actúa antes que Spring.
                        .requestMatchers("/webauthn/register/**").permitAll()
                        // Todo lo demás cerrado, incluida la ruta para pedir tokens por la web (/ott/generate).
                        .anyRequest().denyAll())
                // CSRF: el token va en una cookie legible (XSRF-TOKEN) y vuelve en la cabecera X-XSRF-TOKEN
                // o en el campo _csrf. Se compara sin enmascarar: las páginas estándar de Spring lo envían así
                // en una cabecera, y csrf.spa() lo rechazaría. El enmascarado protege del ataque BREACH, que
                // necesita el token dentro de respuestas comprimidas; aquí viaja en la cookie.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // Usa el bean Webauthn4JRelyingPartyOperations de abajo.
                .webAuthn(Customizer.withDefaults())
                .oneTimeTokenLogin(ott -> ott
                        .tokenService(tokens)
                        .authenticationProvider(new ProveedorSesionDeAlta(tokens))
                        // La web nunca genera tokens: solo el comando "acceso", en el servidor.
                        .generateRequestResolver(peticion -> null)
                        .tokenGenerationSuccessHandler((peticion, respuesta, token) -> respuesta.sendError(404))
                        // Tras usar el enlace, directo a registrar la passkey.
                        .successHandler(new SimpleUrlAuthenticationSuccessHandler("/webauthn/register")))
                // La API responde 401 sin sesión, en vez de redirigir a la página de entrada.
                .exceptionHandling(errores -> errores.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                // Después del CSRF (también eliminar una passkey exige el token) y antes de los filtros de
                // passkeys de Spring, que atienden alguna de sus rutas antes que las reglas generales de acceso.
                .addFilterAfter(new FiltroPasskeys(cuenta), CsrfFilter.class);
        return http.build();
    }

    // Reglas de WebAuthn: dominio y origen de la instalación, y verificación del usuario obligatoria.
    @Bean
    Webauthn4JRelyingPartyOperations operacionesWebAuthn(PublicKeyCredentialUserEntityRepository usuarios,
                                                         UserCredentialRepository passkeys,
                                                         @Value("${mitusalud.acceso.dominio}") String dominio,
                                                         @Value("${mitusalud.acceso.origen}") String origen) {
        Webauthn4JRelyingPartyOperations operaciones = new Webauthn4JRelyingPartyOperations(usuarios, passkeys,
                PublicKeyCredentialRpEntity.builder().id(dominio).name("mitusalud").build(), Set.of(origen));
        // Spring pide "preferred" por defecto: con "required" el navegador exige huella, cara o PIN,
        // y el servidor rechaza la respuesta si no la hubo (al registrar y al entrar).
        operaciones.setCustomizeCreationOptions(opciones -> opciones.authenticatorSelection(
                AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build()));
        operaciones.setCustomizeRequestOptions(opciones -> opciones.userVerification(UserVerificationRequirement.REQUIRED));
        return operaciones;
    }

    // WebAuthn carga al usuario tras verificar la passkey. Solo existe el propietario, y sin contraseña.
    @Bean
    UserDetailsService usuariosDeSpring(CuentaPropietario cuenta) {
        return nombre -> {
            if (!CuentaPropietario.NOMBRE.equals(nombre) || cuenta.identificador().isEmpty()) {
                throw new UsernameNotFoundException("Usuario desconocido");
            }
            return User.withUsername(nombre).password("").authorities(List.of()).build();
        };
    }
}
