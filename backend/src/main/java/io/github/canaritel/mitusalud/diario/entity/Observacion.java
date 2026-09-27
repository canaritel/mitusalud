package io.github.canaritel.mitusalud.diario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Lo común a cualquier registro del diario: una fila de la tabla {@code observation}.
 *
 * Cada tipo de registro (Peso, y en el futuro agua, energía...) es una subclase con su propia
 * tabla de detalle. Es la herencia JOINED de JPA: guardar un Peso inserta una fila en
 * observation y otra en peso, y Hibernate las une al leer.
 *
 * Es abstract: en el código no puede existir una observación sin su tipo. PostgreSQL, en cambio,
 * no puede impedir una fila de observation sin detalle; eso lo garantiza la aplicación
 * guardando ambas en la misma transacción (ver docs/MODELO_DATOS.md, pieza 1).
 */
@Entity
@Table(name = "observation")
@Inheritance(strategy = InheritanceType.JOINED)
// Hibernate escribe en observation.type el valor de @DiscriminatorValue de cada subclase.
@DiscriminatorColumn(name = "type")
public abstract class Observacion {

    // UUID versión 7: lo genera Hibernate antes de insertar. Empieza por la hora de generación,
    // así que no es un identificador secreto (ver docs/MODELO_DATOS.md, pieza 2).
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    // Cuándo ocurrió el hecho. Instant = un instante exacto en la línea de tiempo, sin zona horaria.
    @Column(name = "observed_at", nullable = false)
    private Instant observadoEn;

    // Cuándo se registró. Se fija al crear y no se vuelve a escribir.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant creadoEn;

    // Clave de la operación que creó el registro (cabecera Idempotency-Key). Null en los registros
    // anteriores a V5 y en los tipos que aún no la exigen. No cambia nunca.
    @Column(name = "idempotency_key", updatable = false)
    private UUID claveIdempotencia;

    // JPA necesita un constructor sin argumentos para crear objetos al leer de la base de datos.
    protected Observacion() {
    }

    protected Observacion(Instant observadoEn) {
        this(observadoEn, null);
    }

    protected Observacion(Instant observadoEn, UUID claveIdempotencia) {
        // PostgreSQL guarda hasta microsegundos: se trunca aquí para que el instante en memoria
        // sea exactamente el guardado y dos peticiones del mismo instante se comparen bien.
        this.observadoEn = observadoEn.truncatedTo(ChronoUnit.MICROS);
        this.creadoEn = Instant.now();
        this.claveIdempotencia = claveIdempotencia;
    }

    public UUID getId() {
        return id;
    }

    public Instant getObservadoEn() {
        return observadoEn;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public UUID getClaveIdempotencia() {
        return claveIdempotencia;
    }
}
