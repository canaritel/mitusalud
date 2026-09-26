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

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de punta a punta: controller → service → repository → PostgreSQL, sin nada simulado.
 * Comprueba que las capas encajan entre sí, algo que los tests de cada capa por separado no ven.
 *
 * Alcance: MockMvc simula la petición HTTP sin red, y todo ocurre en una transacción que se
 * deshace al terminar. No comprueba una conexión HTTP real ni que el dato sobreviva entre
 * transacciones separadas; para eso haría falta @SpringBootTest(webEnvironment = RANDOM_PORT)
 * con un cliente HTTP real.
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@SpringBootTest
@AutoConfigureMockMvc
@UsaBaseDeDatosDeTest
@Transactional // deshace lo escrito al terminar; MockMvc corre en el mismo hilo y la misma transacción
class PesoApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unPesoRegistradoPorLaApiApareceEnLaListaConSusDatos() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/pesos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "2026-09-26", "kilos": 71.80}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Se guarda el id real asignado por PostgreSQL para buscar exactamente ese registro,
        // y no cualquier otro que casualmente pese lo mismo.
        Number id = JsonPath.read(respuesta, "$.id");

        // "$[?(@.id == 5)]" es un filtro de JsonPath: los elementos de la lista con ese id.
        // contains(...) exige que haya exactamente uno y con ese valor.
        mockMvc.perform(get("/api/v1/pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %s)].fecha", id).value(contains("2026-09-26")))
                .andExpect(jsonPath("$[?(@.id == %s)].kilos", id).value(contains(71.8)));
    }
}
