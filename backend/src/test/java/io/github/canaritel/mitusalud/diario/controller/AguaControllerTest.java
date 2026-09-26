package io.github.canaritel.mitusalud.diario.controller;

import io.github.canaritel.mitusalud.comun.ConfiguracionIdioma;
import io.github.canaritel.mitusalud.diario.dto.AguaEntrada;
import io.github.canaritel.mitusalud.diario.dto.AguaSalida;
import io.github.canaritel.mitusalud.diario.service.AguaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la capa web del agua (sin base de datos; el service es un mock).
 */
@WebMvcTest(AguaController.class)
@Import(ConfiguracionIdioma.class)
class AguaControllerTest {

    private static final UUID ID = UUID.fromString("0192a4e1-7c3b-7f2a-9d41-3b8e5c1f0a30");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AguaService aguaService;

    @Test
    void registrarAguaValidaDevuelve201() throws Exception {
        when(aguaService.registrar(any(AguaEntrada.class)))
                .thenReturn(new AguaSalida(ID, Instant.parse("2026-09-26T08:00:00Z"), 250));

        mockMvc.perform(post("/api/v1/agua")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"observadoEn": "2026-09-26T10:00:00+02:00", "mililitros": 250}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.observadoEn").value("2026-09-26T08:00:00Z"))
                .andExpect(jsonPath("$.mililitros").value(250));
    }

    @Test
    void ceroMililitrosSeRechaza() throws Exception {
        registrarConMililitros("0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.mililitros").exists());
        verify(aguaService, never()).registrar(any());
    }

    @Test
    void masDe5000MililitrosSeRechaza() throws Exception {
        registrarConMililitros("5001")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.mililitros").exists());
        verify(aguaService, never()).registrar(any());
    }

    @Test
    void unDecimalSeRechazaSinTruncar() throws Exception {
        // 250.5 no debe convertirse en 250 en silencio: se rechaza (docs/MODELO_DATOS.md, pieza 7).
        registrarConMililitros("250.5")
                .andExpect(status().isBadRequest());
        verify(aguaService, never()).registrar(any());
    }

    @Test
    void sinMomentoIndicaElCampoQueFalta() throws Exception {
        mockMvc.perform(post("/api/v1/agua")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mililitros": 250}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.observadoEn").value("no debe ser nulo"));
    }

    @Test
    void listarDevuelveLosRegistrosQueDaElService() throws Exception {
        when(aguaService.listar()).thenReturn(List.of(
                new AguaSalida(ID, Instant.parse("2026-09-26T08:00:00Z"), 250)));

        mockMvc.perform(get("/api/v1/agua"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mililitros").value(250));
    }

    private org.springframework.test.web.servlet.ResultActions registrarConMililitros(String mililitros) throws Exception {
        return mockMvc.perform(post("/api/v1/agua")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"observadoEn": "2026-09-26T10:00:00+02:00", "mililitros": %s}
                        """.formatted(mililitros)));
    }
}
