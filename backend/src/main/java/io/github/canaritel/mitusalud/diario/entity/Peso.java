package io.github.canaritel.mitusalud.diario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un registro de peso: una fila de la tabla {@code peso}.
 * La tabla la crea Flyway (V1__crear_tabla_peso.sql); esta clase solo la refleja.
 *
 * Provisional: es el esqueleto que valida la cadena completa. En el modelo definitivo del
 * backlog el peso será una Observation (con propietario, auditoría y revisiones).
 * Ver docs/STACK.md, sección "Esqueleto provisional".
 */
@Entity
@Table(name = "peso")
public class Peso {

    // PostgreSQL genera el id al insertar (GENERATED ALWAYS AS IDENTITY).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    // BigDecimal y no double: los kilos se guardan con decimales exactos.
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal kilos;

    // Se fija una vez al crear el registro y no se vuelve a escribir.
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    // JPA necesita un constructor sin argumentos para crear objetos al leer de la base de datos.
    // Es protected para que el resto del código use el constructor de abajo.
    protected Peso() {
    }

    public Peso(LocalDate fecha, BigDecimal kilos) {
        this.fecha = fecha;
        this.kilos = kilos;
        this.creadoEn = Instant.now();
    }

    // Solo getters: un registro de peso no se modifica desde fuera una vez creado.
    public Long getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getKilos() {
        return kilos;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
