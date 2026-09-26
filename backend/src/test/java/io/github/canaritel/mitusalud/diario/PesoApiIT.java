package io.github.canaritel.mitusalud.diario;

import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de punta a punta: HTTP → controller → service → repository → PostgreSQL, sin nada simulado.
 * Comprueba que las capas encajan entre sí, algo que los tests de cada capa por separado no ven.
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 *
 * @SpringBootTest arranca la aplicación completa. @Transactional hace que todo lo que el test
 * escriba se deshaga al terminar: MockMvc ejecuta la petición en el mismo hilo y en la misma
 * transacción que el test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@UsaBaseDeDatosDeTest
@Transactional
class PesoApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unPesoRegistradoPorLaApiApareceEnLaLista() throws Exception {
        mockMvc.perform(post("/api/v1/pesos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "2026-09-26", "kilos": 71.80}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber()); // id real, asignado por PostgreSQL

        mockMvc.perform(get("/api/v1/pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].kilos", hasItem(71.8)));
    }
}
