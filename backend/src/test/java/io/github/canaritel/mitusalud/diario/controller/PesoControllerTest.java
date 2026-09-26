package io.github.canaritel.mitusalud.diario.controller;

import io.github.canaritel.mitusalud.comun.ConfiguracionIdioma;
import io.github.canaritel.mitusalud.diario.dto.PesoEntrada;
import io.github.canaritel.mitusalud.diario.dto.PesoSalida;
import io.github.canaritel.mitusalud.diario.service.PesoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la capa web del peso.
 *
 * @WebMvcTest arranca solo lo necesario para HTTP: el controller, la validación, la conversión
 * JSON y el manejador de errores. No arranca la base de datos, así que es rápido y no necesita el túnel.
 * El service se sustituye por un "mock" (@MockitoBean): un objeto falso al que le decimos qué devolver.
 */
@WebMvcTest(PesoController.class)
@Import(ConfiguracionIdioma.class) // @WebMvcTest no carga las clases @Configuration propias; esta hace falta para los idiomas.
class PesoControllerTest {

    @Autowired
    private MockMvc mockMvc; // simula peticiones HTTP sin arrancar un servidor real

    @MockitoBean
    private PesoService pesoService;

    @Test
    void registrarUnPesoValidoDevuelve201ConElRegistro() throws Exception {
        when(pesoService.registrar(any(PesoEntrada.class)))
                .thenReturn(new PesoSalida(1L, LocalDate.of(2026, 9, 26), new BigDecimal("72.35")));

        mockMvc.perform(post("/api/v1/pesos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "2026-09-26", "kilos": 72.35}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fecha").value("2026-09-26"))
                .andExpect(jsonPath("$.kilos").value(72.35));
    }

    @Test
    void registrarConCamposInvalidosDevuelve400YNoLlegaAlService() throws Exception {
        // Fecha calculada para que siempre sea futura, pase el año que pase.
        String manana = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post("/api/v1/pesos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "%s", "kilos": -5}
                                """.formatted(manana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.errores.fecha").exists())
                .andExpect(jsonPath("$.errores.kilos").value("debe ser mayor que 0"));

        // Si la validación falla, el controller no debe llamar al service.
        verify(pesoService, never()).registrar(any());
    }

    @Test
    void registrarSinFechaIndicaElCampoQueFalta() throws Exception {
        mockMvc.perform(post("/api/v1/pesos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kilos": 70}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fecha").value("no debe ser nulo"))
                .andExpect(jsonPath("$.errores.kilos").doesNotExist());
    }

    @Test
    void losErroresSalenEnInglesSiElNavegadorLoPide() throws Exception {
        mockMvc.perform(post("/api/v1/pesos")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "2026-09-26", "kilos": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.errores.kilos").value("must be greater than 0"));
    }

    @Test
    void unIdiomaNoAdmitidoRespondeEnEspanol() throws Exception {
        mockMvc.perform(post("/api/v1/pesos")
                        .header("Accept-Language", "fr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "2026-09-26", "kilos": -5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.kilos").value("debe ser mayor que 0"));
    }

    @Test
    void unJsonMalFormadoDevuelve400ConMensajeClaro() throws Exception {
        mockMvc.perform(post("/api/v1/pesos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "2026-09-26", "kilos": "mucho"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("El cuerpo de la petición no es un JSON válido o tiene valores del tipo equivocado."));
    }

    @Test
    void listarDevuelveLosRegistrosQueDaElService() throws Exception {
        when(pesoService.listar()).thenReturn(List.of(
                new PesoSalida(2L, LocalDate.of(2026, 9, 26), new BigDecimal("72.10")),
                new PesoSalida(1L, LocalDate.of(2026, 9, 25), new BigDecimal("72.35"))));

        mockMvc.perform(get("/api/v1/pesos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].kilos").value(72.35));
    }
}
