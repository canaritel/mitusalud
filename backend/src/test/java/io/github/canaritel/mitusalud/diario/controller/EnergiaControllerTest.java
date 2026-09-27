package io.github.canaritel.mitusalud.diario.controller;

import io.github.canaritel.mitusalud.comun.ConfiguracionIdioma;
import io.github.canaritel.mitusalud.diario.dto.EnergiaEntrada;
import io.github.canaritel.mitusalud.diario.dto.EnergiaSalida;
import io.github.canaritel.mitusalud.diario.service.EnergiaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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
 * Tests de la capa web de la energía (sin base de datos; el service es un mock).
 * Cómo se guarda la nota (por ejemplo, una nota en blanco) se prueba en EnergiaRepositoryIT.
 */
@WebMvcTest(EnergiaController.class)
// Sin los filtros de seguridad: aquí se prueban validación y errores; la seguridad, en acceso/AccesoIT.
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionIdioma.class)
class EnergiaControllerTest {

    private static final UUID ID = UUID.fromString("0192a4e1-7c3b-7f2a-9d41-3b8e5c1f0a31");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnergiaService energiaService;

    @Test
    void registrarEnergiaConNotaDevuelve201() throws Exception {
        when(energiaService.registrar(any(EnergiaEntrada.class)))
                .thenReturn(new EnergiaSalida(ID, Instant.parse("2026-09-26T14:00:00Z"), 2, "tras comer"));

        registrar("""
                {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": 2, "nota": "tras comer"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.observadoEn").value("2026-09-26T14:00:00Z"))
                .andExpect(jsonPath("$.nivel").value(2))
                .andExpect(jsonPath("$.nota").value("tras comer"));
    }

    @Test
    void laNotaEsOpcional() throws Exception {
        when(energiaService.registrar(any(EnergiaEntrada.class)))
                .thenReturn(new EnergiaSalida(ID, Instant.parse("2026-09-26T14:00:00Z"), 4, null));

        registrar("""
                {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": 4}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void unNivelFueraDe1a5SeRechaza(int nivel) throws Exception {
        registrar("""
                {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": %d}
                """.formatted(nivel))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nivel").exists());
        verify(energiaService, never()).registrar(any());
    }

    @Test
    void unNivelDecimalSeRechazaSinTruncar() throws Exception {
        registrar("""
                {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": 2.5}
                """)
                .andExpect(status().isBadRequest());
        verify(energiaService, never()).registrar(any());
    }

    @Test
    void unaNotaDeMasDe500SeRechaza() throws Exception {
        registrar("""
                {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": 3, "nota": "%s"}
                """.formatted("a".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nota").exists());
        verify(energiaService, never()).registrar(any());
    }

    // JSON permite escribir estos caracteres como secuencias de escape, pero no son texto válido
    // para guardar: el carácter nulo (U+0000) y mitades sueltas de emoji (U+D800, U+DE00).
    // La doble barra hace que Java deje la secuencia tal cual y sea Jackson quien la convierta.
    @ParameterizedTest
    @ValueSource(strings = {"con\\u0000nulo", "mitad\\ud800suelta", "mitad\\ude00suelta"})
    void unaNotaConCaracteresNoValidosSeRechaza(String notaEnJson) throws Exception {
        registrar("""
                {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": 3, "nota": "%s"}
                """.formatted(notaEnJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nota").value("contiene caracteres no válidos"));
        verify(energiaService, never()).registrar(any());
    }

    @Test
    void unaNotaConEmojisYSaltosDeLineaSeAcepta() throws Exception {
        when(energiaService.registrar(any(EnergiaEntrada.class)))
                .thenReturn(new EnergiaSalida(ID, Instant.parse("2026-09-26T14:00:00Z"), 5, "x"));

        // 😀 es un emoji completo (dos mitades juntas): no debe confundirse con una mitad suelta.
        registrar("""
                {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": 5, "nota": "¡Genial! 😀\\nMañana más"}
                """)
                .andExpect(status().isCreated());
    }

    @Test
    void listarDevuelveLosRegistrosQueDaElService() throws Exception {
        when(energiaService.listar()).thenReturn(List.of(
                new EnergiaSalida(ID, Instant.parse("2026-09-26T14:00:00Z"), 2, null)));

        mockMvc.perform(get("/api/v1/energia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nivel").value(2));
    }

    private ResultActions registrar(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/energia")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
