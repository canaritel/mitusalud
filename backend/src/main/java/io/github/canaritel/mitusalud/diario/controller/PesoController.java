package io.github.canaritel.mitusalud.diario.controller;

import io.github.canaritel.mitusalud.diario.dto.PesoEntrada;
import io.github.canaritel.mitusalud.diario.dto.PesoSalida;
import io.github.canaritel.mitusalud.diario.service.PesoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API HTTP del peso. El controller solo traduce HTTP a llamadas al service:
 * no contiene reglas de negocio ni habla con la base de datos.
 */
@RestController
@RequestMapping("/api/v1/pesos")
public class PesoController {

    private final PesoService pesoService;

    public PesoController(PesoService pesoService) {
        this.pesoService = pesoService;
    }

    // POST /api/v1/pesos con {"fecha": "2026-09-26", "kilos": 72.35}
    // @Valid aplica las reglas de PesoEntrada; si fallan, Spring responde 400 con un ProblemDetail.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PesoSalida registrar(@Valid @RequestBody PesoEntrada entrada) {
        return pesoService.registrar(entrada);
    }

    // GET /api/v1/pesos: todos los registros, del más reciente al más antiguo.
    @GetMapping
    public List<PesoSalida> listar() {
        return pesoService.listar();
    }
}
