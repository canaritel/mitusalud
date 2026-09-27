package io.github.canaritel.mitusalud.diario;

import com.jayway.jsonpath.JsonPath;
import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import io.github.canaritel.mitusalud.diario.dto.AguaEntrada;
import io.github.canaritel.mitusalud.diario.service.AguaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotencia del registro de agua contra PostgreSQL real (docs/MODELO_DATOS.md, pieza 4, versión mínima).
 *
 * Sin @Transactional a propósito: reintentos y concurrencia solo se prueban con transacciones que se
 * confirman de verdad. Por eso cada test borra al final lo que ha creado.
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@SpringBootTest
@AutoConfigureMockMvc
@UsaBaseDeDatosDeTest
class AguaIdempotenciaIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AguaService aguaService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private final UUID clave = UUID.randomUUID();

    @AfterEach
    void borrarLoCreado() {
        // ON DELETE CASCADE borra también la fila de agua.
        jdbc.update("DELETE FROM observation WHERE idempotency_key = ?", clave);
    }

    @Test
    void unReintentoTrasPerderLaRespuestaDevuelveElMismoRegistro() throws Exception {
        // Primer envío: el servidor lo guarda, pero el cliente pierde la respuesta.
        String respuesta = registrar(clave, "2026-09-26T10:00:00+02:00", 250)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        // Reintento con la misma clave y el mismo instante, escrito con otro desplazamiento horario.
        registrar(clave, "2026-09-26T08:00:00Z", 250)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(filasConLaClave()).isEqualTo(1);
    }

    @Test
    void laMismaClaveConOtrosDatosDevuelve422YNoCreaNada() throws Exception {
        registrar(clave, "2026-09-26T10:00:00+02:00", 250).andExpect(status().isCreated());

        registrar(clave, "2026-09-26T10:00:00+02:00", 330)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Clave ya usada"));

        assertThat(filasConLaClave()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT a.mililitros FROM agua a JOIN observation o ON o.id = a.observation_id
                WHERE o.idempotency_key = ?""", Integer.class, clave)).isEqualTo(250);
    }

    @Test
    void siFallaLaCreacionLaClaveQuedaLibre() {
        OffsetDateTime momento = OffsetDateTime.parse("2026-09-26T10:00:00+02:00");

        // Se llama al service sin la validación del controller, así que 6000 ml llegan a PostgreSQL.
        // Hibernate inserta primero la observación, con la clave, y después falla el CHECK de agua:
        // el fallo ocurre dentro de la transacción, con la clave ya insertada, y no es un choque de claves.
        assertThatThrownBy(() -> aguaService.registrar(clave, new AguaEntrada(momento, 6000)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("agua_mililitros_check");
        assertThat(filasConLaClave()).isZero();

        // La transacción se deshizo entera: un reintento válido con la misma clave completa la operación.
        aguaService.registrar(clave, new AguaEntrada(momento, 250));
        assertThat(filasConLaClave()).isEqualTo(1);
    }

    @Test
    void dosPeticionesSimultaneasConLaMismaClaveDejanUnSoloRegistro() throws Exception {
        UUID idPrimera = UUID.randomUUID();
        ExecutorService otroHilo = Executors.newSingleThreadExecutor();
        Future<MvcResult> segunda;

        try (Connection primera = dataSource.getConnection()) {
            // 1. La primera petición, simulada a mano: inserta observación y detalle con la clave y NO confirma.
            primera.setAutoCommit(false);
            try (PreparedStatement observacion = primera.prepareStatement("""
                         INSERT INTO observation (id, type, observed_at, created_at, idempotency_key)
                         VALUES (?, 'water', ?, now(), ?)""");
                 PreparedStatement agua = primera.prepareStatement(
                         "INSERT INTO agua (observation_id, mililitros) VALUES (?, 250)")) {
                observacion.setObject(1, idPrimera);
                observacion.setObject(2, OffsetDateTime.parse("2026-09-26T08:00:00Z"));
                observacion.setObject(3, clave);
                observacion.executeUpdate();
                agua.setObject(1, idPrimera);
                agua.executeUpdate();
            }

            // 2. La segunda, por la API y en otro hilo. No ve la primera (sin confirmar), intenta insertar
            //    y PostgreSQL la hace esperar en el índice UNIQUE de la clave.
            segunda = otroHilo.submit(() -> registrar(clave, "2026-09-26T10:00:00+02:00", 250).andReturn());

            // 3. Lanzarla en otro hilo no garantiza el orden: se comprueba que está esperando de verdad.
            esperarAUnInsertBloqueado();

            // 4. Al confirmar la primera, la segunda choca con la clave, relee y devuelve ese mismo registro.
            primera.commit();
        } finally {
            otroHilo.shutdown();
        }

        MvcResult resultado = segunda.get(10, TimeUnit.SECONDS);
        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<String>read(resultado.getResponse().getContentAsString(), "$.id"))
                .isEqualTo(idPrimera.toString());
        assertThat(filasConLaClave()).isEqualTo(1);
    }

    // pg_stat_activity muestra las sesiones de PostgreSQL; wait_event_type = 'Lock' es que esperan un bloqueo.
    private void esperarAUnInsertBloqueado() throws InterruptedException {
        for (int intento = 0; intento < 100; intento++) {
            Integer bloqueados = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock'
                      AND query ILIKE 'insert into observation%'""", Integer.class);
            if (bloqueados != null && bloqueados > 0) {
                return;
            }
            Thread.sleep(50);
        }
        fail("La segunda petición no llegó a esperar a la primera: el test no demostraría nada");
    }

    private ResultActions registrar(UUID claveIdempotencia, String observadoEn, int mililitros) throws Exception {
        return mockMvc.perform(post("/api/v1/agua")
                .header("Idempotency-Key", claveIdempotencia.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"observadoEn": "%s", "mililitros": %d}
                        """.formatted(observadoEn, mililitros)));
    }

    private Integer filasConLaClave() {
        return jdbc.queryForObject("SELECT count(*) FROM observation WHERE idempotency_key = ?", Integer.class, clave);
    }
}
