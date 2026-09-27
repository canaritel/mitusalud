package io.github.canaritel.mitusalud.acceso;

import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.github.canaritel.mitusalud.ComoPropietario.propietario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceso del propietario contra PostgreSQL real (backlog L-08 a L-13; docs/MODELO_DATOS.md, pieza 3).
 *
 * Sin @Transactional: los enlaces y las sesiones solo se prueban confirmados de verdad. Cada test empieza
 * sin propietario, passkeys, enlaces ni sesiones. Registrar una passkey real necesita un autenticador:
 * eso se comprueba en el navegador (guía, sección 8).
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@SpringBootTest
@AutoConfigureMockMvc
@UsaBaseDeDatosDeTest
class AccesoIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuentaPropietario cuenta;

    @Autowired
    private PublicKeyCredentialUserEntityRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void sinNadaDeAcceso() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM one_time_tokens");
        jdbc.update("DELETE FROM user_credentials");
        jdbc.update("DELETE FROM owner_account");
        jdbc.update("DELETE FROM user_entities");
    }

    // --- Quién accede a los datos (L-09, L-13) ---

    @Test
    void sinSesionLaApiResponde401() throws Exception {
        mockMvc.perform(get("/api/v1/agua")).andExpect(status().isUnauthorized());
    }

    @Test
    void elPropietarioConSuPasskeyAccedeALosDatos() throws Exception {
        mockMvc.perform(get("/api/v1/agua").with(propietario(cuenta, usuarios))).andExpect(status().isOk());
    }

    @Test
    void otraIdentidadConPasskeyEsRechazada() throws Exception {
        cuenta.crearSiNoExiste();
        PublicKeyCredentialUserEntity otro = ImmutablePublicKeyCredentialUserEntity.builder()
                .name("otro").id(Bytes.random()).displayName("Otro").build();

        mockMvc.perform(get("/api/v1/agua").with(authentication(new WebAuthnAuthentication(otro, List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenCsrfNoSePuedeEscribir() throws Exception {
        cuenta.crearSiNoExiste();
        WebAuthnAuthentication conPasskey = new WebAuthnAuthentication(usuarios.findByUsername(CuentaPropietario.NOMBRE), List.of());

        mockMvc.perform(post("/api/v1/agua").with(authentication(conPasskey))
                        .header("Idempotency-Key", "5f0c7e2a-3b1d-4c8e-9a6f-2d4b8c1e7f30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observadoEn\": \"2026-09-26T10:00:00Z\", \"mililitros\": 250}"))
                .andExpect(status().isForbidden());
    }

    // --- El enlace de un solo uso (L-08) ---

    @Test
    void elEnlaceAbreUnaSesionDeAltaQueNoLeeNiEscribeDatos() throws Exception {
        Cookie sesion = entrarConEnlace(cuenta.primerAcceso());

        mockMvc.perform(get("/api/v1/agua").cookie(sesion)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/agua").cookie(sesion).with(csrf())
                        .header("Idempotency-Key", "5f0c7e2a-3b1d-4c8e-9a6f-2d4b8c1e7f31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observadoEn\": \"2026-09-26T10:00:00Z\", \"mililitros\": 250}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agua", Integer.class)).isZero();
    }

    @Test
    void conLaSesionDeAltaSePideUnaPasskeyConVerificacionDelUsuario() throws Exception {
        Cookie sesion = entrarConEnlace(cuenta.primerAcceso());

        // Las opciones que recibe el navegador exigen huella, cara o PIN, para el dominio de la instalación.
        mockMvc.perform(post("/webauthn/register/options").cookie(sesion).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticatorSelection.userVerification").value("required"))
                .andExpect(jsonPath("$.rp.id").value("localhost"));
    }

    @Test
    void alEntrarConPasskeyTambienSeExigeVerificacionDelUsuario() throws Exception {
        mockMvc.perform(post("/webauthn/authenticate/options").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userVerification").value("required"));
    }

    @Test
    void elEnlaceSoloSirveUnaVez() throws Exception {
        String token = cuenta.primerAcceso();
        entrarConEnlace(token);

        usarEnlace(token).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unEnlaceCaducadoNoSirve() throws Exception {
        cuenta.crearSiNoExiste();
        jdbc.update("INSERT INTO one_time_tokens VALUES ('caducado', ?, now() - interval '1 minute')", CuentaPropietario.NOMBRE);

        usarEnlace("caducado").andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unEnlaceNuevoInvalidaElAnterior() throws Exception {
        String anterior = cuenta.primerAcceso();
        String nuevo = cuenta.primerAcceso();

        usarEnlace(anterior).andExpect(redirectedUrl("/login?error"));
        usarEnlace(nuevo).andExpect(redirectedUrl("/webauthn/register"));
    }

    @Test
    void laWebNoGeneraEnlaces() throws Exception {
        cuenta.crearSiNoExiste();

        MvcResult resultado = mockMvc.perform(post("/ott/generate").param("username", CuentaPropietario.NOMBRE).with(csrf()))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM one_time_tokens", Integer.class)).isZero();
    }

    @Test
    void unaSesionDeAltaCaducadaNoPuedeRegistrarPasskeys() throws Exception {
        cuenta.crearSiNoExiste();
        SesionDeAlta caducada = new SesionDeAlta(CuentaPropietario.NOMBRE, Instant.now().minusSeconds(1));

        mockMvc.perform(post("/webauthn/register/options").with(authentication(caducada)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void primerAccesoSeNiegaSiYaHayPasskeys() {
        cuenta.crearSiNoExiste();
        registrarPasskeyFalsa("pk-1");

        assertThatThrownBy(() -> cuenta.primerAcceso())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acceso recuperar");
    }

    // --- Recuperación y eliminación (L-10) ---

    @Test
    void recuperarSinPropietarioSeNiega() {
        assertThatThrownBy(() -> cuenta.recuperar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acceso primero");
    }

    @Test
    void laRecuperacionRevocaLasPasskeysCierraLasSesionesEInvalidaLosEnlaces() throws Exception {
        Cookie sesionAnterior = entrarConEnlace(cuenta.primerAcceso());
        String identificador = cuenta.identificador().orElseThrow();
        registrarPasskeyFalsa("pk-1");
        registrarPasskeyFalsa("pk-2");
        // Un enlace emitido antes y todavía vigente.
        jdbc.update("INSERT INTO one_time_tokens VALUES ('anterior', ?, now() + interval '5 minutes')", CuentaPropietario.NOMBRE);

        String nuevo = cuenta.recuperar();

        assertThat(cuenta.passkeys()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isZero();
        mockMvc.perform(get("/webauthn/register").cookie(sesionAnterior)).andExpect(status().isUnauthorized());
        usarEnlace("anterior").andExpect(redirectedUrl("/login?error"));
        // El propietario sigue siendo el mismo, y el enlace nuevo funciona.
        assertThat(cuenta.identificador()).contains(identificador);
        usarEnlace(nuevo).andExpect(redirectedUrl("/webauthn/register"));
    }

    @Test
    void noSePuedeEliminarLaUltimaPasskey() throws Exception {
        cuenta.crearSiNoExiste();
        registrarPasskeyFalsa("pk-1");

        mockMvc.perform(delete("/webauthn/register/{id}", "pk-1").with(propietario(cuenta, usuarios)))
                .andExpect(status().isConflict());
        assertThat(cuenta.passkeys()).isEqualTo(1);
    }

    @Test
    void eliminarUnaPasskeyCierraTodasLasSesiones() throws Exception {
        Cookie otraSesion = entrarConEnlace(cuenta.primerAcceso());
        registrarPasskeyFalsa("pk-1");
        registrarPasskeyFalsa("pk-2");

        mockMvc.perform(delete("/webauthn/register/{id}", "pk-1").with(propietario(cuenta, usuarios)))
                .andExpect(status().is2xxSuccessful());

        assertThat(cuenta.passkeys()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isZero();
        mockMvc.perform(get("/webauthn/register").cookie(otraSesion)).andExpect(status().isUnauthorized());
    }

    @Test
    void unEnlaceNuevoCierraLaSesionDeAltaDelAnterior() throws Exception {
        Cookie sesionAnterior = entrarConEnlace(cuenta.primerAcceso());

        cuenta.primerAcceso();

        mockMvc.perform(post("/webauthn/register/options").cookie(sesionAnterior).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaSesionDeAltaNoAnadePasskeysSiYaHayUna() throws Exception {
        cuenta.crearSiNoExiste();
        registrarPasskeyFalsa("pk-1");
        SesionDeAlta vigente = new SesionDeAlta(CuentaPropietario.NOMBRE, Instant.now().plus(SesionDeAlta.DURACION));

        mockMvc.perform(post("/webauthn/register/options").with(authentication(vigente)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void dosEliminacionesSimultaneasNoDejanAlPropietarioSinPasskeys() throws Exception {
        cuenta.crearSiNoExiste();
        registrarPasskeyFalsa("pk-1");
        registrarPasskeyFalsa("pk-2");
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Future<CuentaPropietario.Eliminacion> primera;
        Future<CuentaPropietario.Eliminacion> segunda;

        // El test bloquea a mano la fila del propietario: las dos eliminaciones tienen que esperar.
        try (Connection bloqueo = dataSource.getConnection()) {
            bloqueo.setAutoCommit(false);
            bloqueo.createStatement().executeQuery("SELECT user_handle FROM owner_account FOR UPDATE").close();

            primera = hilos.submit(() -> cuenta.eliminarPasskey("pk-1"));
            segunda = hilos.submit(() -> cuenta.eliminarPasskey("pk-2"));
            esperarBloqueadas(2);

            bloqueo.commit(); // al soltarla, pasan de una en una
        } finally {
            hilos.shutdown();
        }

        assertThat(List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(CuentaPropietario.Eliminacion.HECHA, CuentaPropietario.Eliminacion.ES_LA_ULTIMA);
        assertThat(cuenta.passkeys()).isEqualTo(1);
    }

    @Test
    void siFallaElCierreDeSesionesNoSeEliminaLaPasskey() {
        cuenta.crearSiNoExiste();
        registrarPasskeyFalsa("pk-1");
        registrarPasskeyFalsa("pk-2");
        // Un fallo provocado en PostgreSQL justo al cerrar las sesiones, dentro de la transacción de eliminar.
        jdbc.execute("CREATE FUNCTION fallo_provocado() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'fallo provocado'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER fallo_al_cerrar_sesiones BEFORE DELETE ON spring_session FOR EACH STATEMENT EXECUTE FUNCTION fallo_provocado()");
        try {
            assertThatThrownBy(() -> cuenta.eliminarPasskey("pk-1")).hasMessageContaining("fallo provocado");
            assertThat(cuenta.passkeys()).isEqualTo(2);
        } finally {
            jdbc.execute("DROP TRIGGER fallo_al_cerrar_sesiones ON spring_session");
            jdbc.execute("DROP FUNCTION fallo_provocado()");
        }
    }

    // --- Un solo propietario (L-12) ---

    @Test
    void soloPuedeHaberUnPropietario() {
        cuenta.crearSiNoExiste();
        jdbc.update("INSERT INTO user_entities (id, name) VALUES ('otro-id', 'otro')");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO owner_account (user_handle) VALUES ('otro-id')"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("owner_account_una_sola_fila");
    }

    // --- Ayudas ---

    private org.springframework.test.web.servlet.ResultActions usarEnlace(String token) throws Exception {
        return mockMvc.perform(post("/login/ott").param("token", token).with(csrf()));
    }

    // pg_stat_activity: espera a que haya tantas sesiones de PostgreSQL bloqueadas por el FOR UPDATE.
    private void esperarBloqueadas(int cuantas) throws InterruptedException {
        for (int intento = 0; intento < 100; intento++) {
            Integer bloqueadas = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock'
                      AND query ILIKE '%owner_account FOR UPDATE%'""", Integer.class);
            if (bloqueadas != null && bloqueadas >= cuantas) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Las eliminaciones no llegaron a esperar al bloqueo: el test no demostraría nada");
    }

    // Usa el enlace y devuelve la cookie de la sesión de alta que abre.
    private Cookie entrarConEnlace(String token) throws Exception {
        MvcResult resultado = usarEnlace(token).andExpect(redirectedUrl("/webauthn/register")).andReturn();
        Cookie sesion = resultado.getResponse().getCookie("SESSION");
        assertThat(sesion).isNotNull();
        return sesion;
    }

    // Una passkey guardada directamente en la tabla: basta para contar, revocar y eliminar.
    // Crear una de verdad exige un autenticador, y eso se comprueba en el navegador.
    private void registrarPasskeyFalsa(String idCredencial) {
        jdbc.update("""
                INSERT INTO user_credentials (credential_id, user_entity_user_id, public_key, signature_count,
                    uv_initialized, backup_eligible, authenticator_transports, public_key_credential_type, backup_state,
                    attestation_object, attestation_client_data_json, created, last_used, label)
                VALUES (?, ?, '\\x00'::bytea, 0, true, false, 'internal', 'public-key', false,
                    '\\x00'::bytea, '\\x00'::bytea, now(), now(), 'prueba')""",
                idCredencial, cuenta.identificador().orElseThrow());
    }
}
