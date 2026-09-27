package io.github.canaritel.mitusalud.diario.controller;

import io.github.canaritel.mitusalud.diario.dto.AguaEntrada;
import io.github.canaritel.mitusalud.diario.dto.AguaSalida;
import io.github.canaritel.mitusalud.diario.service.AguaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API HTTP del agua. Ruta en singular (/agua): el agua no se cuenta en unidades.
 */
@RestController
@RequestMapping("/api/v1/agua")
public class AguaController {

    private final AguaService aguaService;

    public AguaController(AguaService aguaService) {
        this.aguaService = aguaService;
    }

    // POST /api/v1/agua con la cabecera "Idempotency-Key: <UUID>"
    // y {"observadoEn": "2026-09-26T10:00:00+02:00", "mililitros": 250}.
    // La clave identifica la operación: un reintento repite la misma; otro vaso lleva otra.
    // Sin cabecera, o si no es un UUID, responde 400.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AguaSalida registrar(@RequestHeader("Idempotency-Key") UUID clave, @Valid @RequestBody AguaEntrada entrada) {
        return aguaService.registrar(clave, entrada);
    }

    // GET /api/v1/agua: todos los registros de agua, del más reciente al más antiguo.
    @GetMapping
    public List<AguaSalida> listar() {
        return aguaService.listar();
    }
}
