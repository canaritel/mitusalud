package io.github.canaritel.mitusalud.diario;

import com.jayway.jsonpath.JsonPath;
import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import io.github.canaritel.mitusalud.acceso.CuentaPropietario;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static io.github.canaritel.mitusalud.ComoPropietario.propietario;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Punta a punta de la energía: controller → service → repository → PostgreSQL.
 * Mismo alcance que AguaApiIT (MockMvc y una transacción que se deshace).
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@SpringBootTest
@AutoConfigureMockMvc
@UsaBaseDeDatosDeTest
@Transactional
class EnergiaApiIT {

    @Autowired
    private MockMvc mockMvc;

    // Para entrar como el propietario con su passkey (ver ComoPropietario).
    @Autowired
    private CuentaPropietario cuenta;

    @Autowired
    private PublicKeyCredentialUserEntityRepository usuarios;

    @Autowired
    private EntityManager entityManager;

    @Test
    void energiaConNotaRegistradaPorLaApiApareceEnSuListaYNoEnLasOtras() throws Exception {
        // Tildes, ñ y un emoji: la nota debe volver exactamente igual desde PostgreSQL.
        String nota = "Bajón después de comer, mañana pruebo a caminar 🚶";

        String respuesta = mockMvc.perform(post("/api/v1/energia").with(propietario(cuenta, usuarios))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": 2, "nota": "%s"}
                                """.formatted(nota)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observadoEn").value("2026-09-26T14:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        // POST y GET comparten transacción: sin esto, Hibernate devolvería la entidad que ya tiene
        // en memoria y el test pasaría aunque PostgreSQL hubiera guardado otra cosa.
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/energia").with(propietario(cuenta, usuarios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].nivel", id).value(contains(2)))
                .andExpect(jsonPath("$[?(@.id == '%s')].nota", id).value(contains(nota)));

        // El mismo id no aparece en las listas de agua ni de pesos.
        mockMvc.perform(get("/api/v1/agua").with(propietario(cuenta, usuarios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]", id).isEmpty());
        mockMvc.perform(get("/api/v1/pesos").with(propietario(cuenta, usuarios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]", id).isEmpty());
    }
}
