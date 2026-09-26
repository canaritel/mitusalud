package io.github.canaritel.mitusalud.diario.repository;

import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import io.github.canaritel.mitusalud.diario.entity.Agua;
import io.github.canaritel.mitusalud.diario.entity.Peso;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del agua contra PostgreSQL real, y de la separación entre tipos:
 * con dos tipos en la misma tabla observation, ninguno debe mezclarse con el otro.
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@UsaBaseDeDatosDeTest
class AguaRepositoryIT {

    private static final Instant MOMENTO = Instant.parse("2026-09-26T08:00:00Z");

    @Autowired
    private AguaRepository aguaRepository;

    @Autowired
    private PesoRepository pesoRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void guardarAguaCreaUnaFilaEnCadaTabla() {
        UUID id = aguaRepository.saveAndFlush(new Agua(MOMENTO, 250)).getId();

        Object tipoEnObservation = entityManager
                .createNativeQuery("SELECT type FROM observation WHERE id = :id")
                .setParameter("id", id).getSingleResult();
        Object[] filaAgua = (Object[]) entityManager
                .createNativeQuery("SELECT type, mililitros FROM agua WHERE observation_id = :id")
                .setParameter("id", id).getSingleResult();

        assertThat(tipoEnObservation).isEqualTo("water");
        assertThat(filaAgua[0]).isEqualTo("water");
        assertThat(filaAgua[1]).isEqualTo(250);
    }

    @Test
    void cadaListaSoloTraeSuTipo() {
        UUID idPeso = pesoRepository.save(new Peso(MOMENTO, new BigDecimal("72.35"))).getId();
        UUID idAgua = aguaRepository.save(new Agua(MOMENTO, 250)).getId();
        entityManager.flush();
        entityManager.clear();

        // Al consultar Peso, Hibernate une observation solo con la tabla peso (y lo mismo con agua).
        assertThat(pesoRepository.findAllByOrderByObservadoEnDescIdDesc()).extracting(Peso::getId).containsExactly(idPeso);
        assertThat(aguaRepository.findAllByOrderByObservadoEnDescIdDesc()).extracting(Agua::getId).containsExactly(idAgua);
    }

    @Test
    void postgresqlImpideColgarUnAguaDeUnaObservacionDePeso() {
        UUID idPeso = pesoRepository.saveAndFlush(new Peso(MOMENTO, new BigDecimal("72.35"))).getId();

        // Se salta a Hibernate con SQL directo: la protección tiene que estar en la base de datos.
        // La FK compuesta (observation_id, type) exige una observación de tipo 'water'.
        // SQLState 23503 = violación de clave foránea.
        assertThatThrownBy(() -> entityManager
                .createNativeQuery("INSERT INTO agua (observation_id, mililitros) VALUES (:id, 250)")
                .setParameter("id", idPeso).executeUpdate())
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, causa -> {
                    assertThat(causa.getSQLState()).isEqualTo("23503");
                    assertThat(causa.getMessage()).contains("agua_observation_id_type_fkey");
                });
    }

    @Test
    void observationSigueRechazandoUnTipoDesconocido() {
        // V3 amplía el CHECK a 'weight' y 'water', no lo quita. SQLState 23514 = violación de CHECK.
        assertThatThrownBy(() -> entityManager
                .createNativeQuery("INSERT INTO observation (id, type, observed_at) VALUES (:id, 'sleep', now())")
                .setParameter("id", UUID.randomUUID()).executeUpdate())
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, causa -> {
                    assertThat(causa.getSQLState()).isEqualTo("23514");
                    assertThat(causa.getMessage()).contains("observation_type_check");
                });
    }

    @Test
    void laBaseDeDatosRechazaMasDe5000Mililitros() {
        assertThatThrownBy(() -> aguaRepository.saveAndFlush(new Agua(MOMENTO, 5001)))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, causa -> {
                    assertThat(causa.getSQLState()).isEqualTo("23514");
                    assertThat(causa.getMessage()).contains("agua_mililitros_check");
                });
    }
}
