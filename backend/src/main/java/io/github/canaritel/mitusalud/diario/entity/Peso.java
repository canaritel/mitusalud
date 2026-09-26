package io.github.canaritel.mitusalud.diario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un registro de peso: una observación de tipo "weight" más su fila en la tabla {@code peso}.
 * Las tablas las crea Flyway (V2__peso_como_observacion.sql); estas clases solo las reflejan.
 */
@Entity
@Table(name = "peso")
// Valor que Hibernate escribe en observation.type para los pesos.
@DiscriminatorValue("weight")
// La clave de la tabla peso es observation_id, la misma que la de su observación.
@PrimaryKeyJoinColumn(name = "observation_id")
public class Peso extends Observacion {

    // BigDecimal y no double: los kilos se guardan con decimales exactos.
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal kilos;

    protected Peso() {
    }

    public Peso(Instant observadoEn, BigDecimal kilos) {
        super(observadoEn);
        this.kilos = kilos;
    }

    // Solo getters: un registro de peso no se modifica desde fuera una vez creado.
    public BigDecimal getKilos() {
        return kilos;
    }
}
