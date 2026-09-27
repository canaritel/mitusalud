package io.github.canaritel.mitusalud.diario.controller;

import io.github.canaritel.mitusalud.diario.dto.EnergiaEntrada;
import io.github.canaritel.mitusalud.diario.dto.EnergiaSalida;
import io.github.canaritel.mitusalud.diario.service.EnergiaService;
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
 * API HTTP de la energía. Ruta en singular (/energia), como /agua.
 */
@RestController
@RequestMapping("/api/v1/energia")
public class EnergiaController {

    private final EnergiaService energiaService;

    public EnergiaController(EnergiaService energiaService) {
        this.energiaService = energiaService;
    }

    // POST /api/v1/energia con {"observadoEn": "2026-09-26T16:00:00+02:00", "nivel": 2, "nota": "tras comer"}
    // La nota es opcional: se puede omitir o enviar null.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnergiaSalida registrar(@Valid @RequestBody EnergiaEntrada entrada) {
        return energiaService.registrar(entrada);
    }

    // GET /api/v1/energia: todos los registros de energía, del más reciente al más antiguo.
    @GetMapping
    public List<EnergiaSalida> listar() {
        return energiaService.listar();
    }
}
