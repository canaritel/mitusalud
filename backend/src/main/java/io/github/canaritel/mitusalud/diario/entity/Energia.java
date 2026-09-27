package io.github.canaritel.mitusalud.diario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Un registro de energía: una observación de tipo "energy" más su fila en la tabla {@code energia}.
 * Mismo patrón que {@link Agua}; la tabla la crea Flyway (V4__energia.sql).
 */
@Entity
@Table(name = "energia")
@DiscriminatorValue("energy")
@PrimaryKeyJoinColumn(name = "observation_id")
public class Energia extends Observacion {

    // De 1 (muy baja) a 5 (muy alta). No tiene unidad.
    @Column(nullable = false)
    private Integer nivel;

    // Opcional: null significa "sin nota".
    private String nota;

    protected Energia() {
    }

    public Energia(Instant observadoEn, Integer nivel, String nota) {
        super(observadoEn);
        this.nivel = nivel;
        // Una nota vacía o solo con espacios se guarda como "sin nota"; con texto, tal cual.
        // Aquí y no en el DTO, para que valga con cualquier forma de crear una energía.
        this.nota = (nota == null || nota.isBlank()) ? null : nota;
    }

    public Integer getNivel() {
        return nivel;
    }

    public String getNota() {
        return nota;
    }
}
