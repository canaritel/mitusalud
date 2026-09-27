package io.github.canaritel.mitusalud.diario.repository;

import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import io.github.canaritel.mitusalud.diario.entity.Agua;
import io.github.canaritel.mitusalud.diario.entity.Energia;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la energía contra PostgreSQL real. Solo lo que añade la energía: su FK, sus CHECK
 * y la nota. El CHECK común de observation ya lo prueba AguaRepositoryIT.
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@UsaBaseDeDatosDeTest
// Captura todo lo que se escribe en consola durante el test, incluidos los logs.
@ExtendWith(OutputCaptureExtension.class)
class EnergiaRepositoryIT {

    private static final Instant MOMENTO = Instant.parse("2026-09-26T14:00:00Z");

    // Texto inventado y fácil de buscar: si aparece en los logs, la nota se ha filtrado.
    private static final String MARCADOR = "MARCADOR-NOTA-SINTETICA-7f3a";

    @Autowired
    private EnergiaRepository energiaRepository;

    @Autowired
    private AguaRepository aguaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void guardarEnergiaCreaUnaFilaEnCadaTabla() {
        UUID id = energiaRepository.saveAndFlush(new Energia(MOMENTO, 2, "Cansancio tras la caminata")).getId();

        Object tipoEnObservation = entityManager
                .createNativeQuery("SELECT type FROM observation WHERE id = :id")
                .setParameter("id", id).getSingleResult();
        Object[] filaEnergia = (Object[]) entityManager
                .createNativeQuery("SELECT type, nivel, nota FROM energia WHERE observation_id = :id")
                .setParameter("id", id).getSingleResult();

        assertThat(tipoEnObservation).isEqualTo("energy");
        assertThat(filaEnergia[0]).isEqualTo("energy");
        assertThat(filaEnergia[1]).isEqualTo(2);
        assertThat(filaEnergia[2]).isEqualTo("Cansancio tras la caminata");
    }

    @Test
    void unaNotaEnBlancoSeGuardaComoNull() {
        UUID id = energiaRepository.saveAndFlush(new Energia(MOMENTO, 3, "  \n ")).getId();

        // Se lee con SQL directo: lo que importa es lo que queda en la tabla.
        Object nota = entityManager
                .createNativeQuery("SELECT nota FROM energia WHERE observation_id = :id")
                .setParameter("id", id).getSingleResult();

        assertThat(nota).isNull();
    }

    @Test
    void postgresqlImpideColgarUnaEnergiaDeUnaObservacionDeAgua() {
        UUID idAgua = aguaRepository.saveAndFlush(new Agua(MOMENTO, 250)).getId();

        // Cada tabla de detalle tiene su propia FK: esta prueba detectaría que faltara o estuviera mal escrita.
        // SQLState 23503 = violación de clave foránea.
        assertThatThrownBy(() -> entityManager
                .createNativeQuery("INSERT INTO energia (observation_id, nivel) VALUES (:id, 3)")
                .setParameter("id", idAgua).executeUpdate())
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, causa -> {
                    assertThat(causa.getSQLState()).isEqualTo("23503");
                    assertThat(causa.getMessage()).contains("energia_observation_id_type_fkey");
                });
    }

    @Test
    void laBaseDeDatosRechazaUnNivelFueraDe1a5() {
        assertThatThrownBy(() -> energiaRepository.saveAndFlush(new Energia(MOMENTO, 6, null)))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, causa -> {
                    assertThat(causa.getSQLState()).isEqualTo("23514");
                    assertThat(causa.getMessage()).contains("energia_nivel_check");
                });
    }

    @Test
    void laBaseDeDatosRechazaUnaNotaDeMasDe500SinEscribirlaEnLosLogs(CapturedOutput salida) {
        String notaLarga = MARCADOR + "x".repeat(501 - MARCADOR.length());

        assertThatThrownBy(() -> energiaRepository.saveAndFlush(new Energia(MOMENTO, 3, notaLarga)))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, causa -> {
                    assertThat(causa.getSQLState()).isEqualTo("23514");
                    assertThat(causa.getMessage()).contains("energia_nota_check");
                    // El mensaje de la excepción es lo que acabaría en un log si alguien la registrara.
                    assertThat(causa.getMessage()).doesNotContain(MARCADOR);
                });

        // Por defecto, PostgreSQL añade al error la fila rechazada ("Failing row contains (...)")
        // y Hibernate la escribe en el log. logServerErrorDetail=false lo evita (application.properties).
        assertThat(salida.getAll()).doesNotContain(MARCADOR);
    }
}
