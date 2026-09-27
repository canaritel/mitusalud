package io.github.canaritel.mitusalud.diario;

import com.jayway.jsonpath.JsonPath;
import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Punta a punta del agua: controller → service → repository → PostgreSQL.
 * Mismo alcance que PesoApiIT (MockMvc y una transacción que se deshace).
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@SpringBootTest
@AutoConfigureMockMvc
@UsaBaseDeDatosDeTest
@Transactional
class AguaApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aguaRegistradaPorLaApiApareceEnSuListaYNoEnLaDePesos() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/agua")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"observadoEn": "2026-09-26T10:00:00+02:00", "mililitros": 330}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observadoEn").value("2026-09-26T08:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        mockMvc.perform(get("/api/v1/agua"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].mililitros", id).value(contains(330)));

        // El mismo id no aparece en la lista de pesos.
        mockMvc.perform(get("/api/v1/pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]", id).isEmpty());
    }
}
