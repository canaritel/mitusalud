package io.github.canaritel.mitusalud.diario;

import com.jayway.jsonpath.JsonPath;
import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import io.github.canaritel.mitusalud.acceso.CuentaPropietario;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
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

    // Para entrar como el propietario con su passkey (ver ComoPropietario).
    @Autowired
    private CuentaPropietario cuenta;

    @Autowired
    private PublicKeyCredentialUserEntityRepository usuarios;

    @Test
    void unPesoRegistradoPorLaApiApareceEnLaListaConSusDatos() throws Exception {
        // Enviado con zona +02:00; la API devuelve el mismo instante en UTC.
        String respuesta = mockMvc.perform(post("/api/v1/pesos").with(propietario(cuenta, usuarios))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"observadoEn": "2026-09-26T08:30:00+02:00", "kilos": 71.80}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observadoEn").value("2026-09-26T06:30:00Z"))
                .andReturn().getResponse().getContentAsString();

        // Se guarda el id real (UUID v7) para buscar exactamente ese registro,
        // y no cualquier otro que casualmente pese lo mismo.
        String id = JsonPath.read(respuesta, "$.id");

        // "$[?(@.id == '...')]" es un filtro de JsonPath: los elementos de la lista con ese id.
        // contains(...) exige que haya exactamente uno y con ese valor.
        mockMvc.perform(get("/api/v1/pesos").with(propietario(cuenta, usuarios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].observadoEn", id).value(contains("2026-09-26T06:30:00Z")))
                .andExpect(jsonPath("$[?(@.id == '%s')].kilos", id).value(contains(71.8)));
    }
}
