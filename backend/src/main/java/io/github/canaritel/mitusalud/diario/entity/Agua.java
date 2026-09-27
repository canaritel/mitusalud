package io.github.canaritel.mitusalud.diario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un registro de agua: una observación de tipo "water" más su fila en la tabla {@code agua}.
 * Mismo patrón que {@link Peso}; la tabla la crea Flyway (V3__agua.sql).
 */
@Entity
@Table(name = "agua")
@DiscriminatorValue("water")
@PrimaryKeyJoinColumn(name = "observation_id")
public class Agua extends Observacion {

    // Mililitros enteros: la unidad canónica del agua (docs/MODELO_DATOS.md, pieza 7).
    @Column(nullable = false)
    private Integer mililitros;

    protected Agua() {
    }

    // El agua siempre se crea con la clave de su operación (docs/MODELO_DATOS.md, pieza 4).
    public Agua(Instant observadoEn, Integer mililitros, UUID claveIdempotencia) {
        super(observadoEn, claveIdempotencia);
        this.mililitros = mililitros;
    }

    public Integer getMililitros() {
        return mililitros;
    }
}
