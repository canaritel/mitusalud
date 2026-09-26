package io.github.canaritel.mitusalud.diario.repository;

import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import io.github.canaritel.mitusalud.diario.entity.Peso;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del repository contra un PostgreSQL real (la base mitusalud_test del servidor).
 *
 * Requisitos: túnel SSH abierto y ejecutar con "./mvnw verify -Pbd".
 * El sufijo IT (Integration Test) hace que Maven solo los ejecute con ese perfil.
 *
 * @DataJpaTest arranca solo JPA, Flyway y los repositories. Cada test corre en una transacción
 * que se deshace al terminar, así que la base de datos queda siempre vacía.
 * Al arrancar, Flyway aplica las migraciones y Hibernate valida que las entidades coinciden
 * con las tablas: si no coinciden, fallan todos los tests.
 */
@DataJpaTest
// Por defecto @DataJpaTest sustituye la base de datos por una en memoria. NONE = usar PostgreSQL real.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@UsaBaseDeDatosDeTest // base mitusalud_test, protegida contra configuraciones externas
class PesoRepositoryIT {

    private static final Instant MANANA_DIA_26 = Instant.parse("2026-09-26T06:30:00Z");

    @Autowired
    private PesoRepository pesoRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void guardarUnPesoCreaUnaFilaEnCadaTabla() {
        Peso guardado = pesoRepository.saveAndFlush(new Peso(MANANA_DIA_26, new BigDecimal("72.35")));
        UUID id = guardado.getId();
        assertThat(id).isNotNull(); // lo generó Hibernate (UUID v7)
        assertThat(id.version()).isEqualTo(7);

        // Consultas SQL directas: comprueban lo que hay realmente en cada tabla, sin pasar por la entidad.
        Object tipoEnObservation = entityManager
                .createNativeQuery("SELECT type FROM observation WHERE id = :id")
                .setParameter("id", id).getSingleResult();
        Object[] filaPeso = (Object[]) entityManager
                .createNativeQuery("SELECT type, kilos FROM peso WHERE observation_id = :id")
                .setParameter("id", id).getSingleResult();

        assertThat(tipoEnObservation).isEqualTo("weight"); // lo escribe Hibernate (@DiscriminatorValue)
        assertThat(filaPeso[0]).isEqualTo("weight");       // lo rellena el DEFAULT de PostgreSQL
        assertThat((BigDecimal) filaPeso[1]).isEqualByComparingTo("72.35");
    }

    @Test
    void guardaLosKilosYElMomentoExactos() {
        Peso guardado = pesoRepository.saveAndFlush(new Peso(MANANA_DIA_26, new BigDecimal("72.35")));

        // Hibernate guarda en memoria las entidades que ya conoce (caché de primer nivel).
        // Sin clear(), findById devolvería el mismo objeto de arriba sin preguntar a PostgreSQL
        // y el test pasaría aunque la base de datos hubiera guardado otro valor.
        entityManager.clear();

        Peso leido = pesoRepository.findById(guardado.getId()).orElseThrow();
        // compareTo y no equals: para BigDecimal, 72.35 y 72.350 son iguales en valor pero no en equals.
        assertThat(leido.getKilos()).isEqualByComparingTo("72.35");
        assertThat(leido.getObservadoEn()).isEqualTo(MANANA_DIA_26);
    }

    @Test
    void listaDelMasRecienteAlMasAntiguo() {
        Peso dia24 = pesoRepository.save(new Peso(Instant.parse("2026-09-24T06:30:00Z"), new BigDecimal("73.00")));
        Peso manana26 = pesoRepository.save(new Peso(MANANA_DIA_26, new BigDecimal("72.50")));
        Peso noche26 = pesoRepository.save(new Peso(Instant.parse("2026-09-26T20:00:00Z"), new BigDecimal("72.90")));

        List<Peso> lista = pesoRepository.findAllByOrderByObservadoEnDescIdDesc();

        assertThat(lista).extracting(Peso::getId)
                .containsExactly(noche26.getId(), manana26.getId(), dia24.getId());
    }

    @Test
    void registrosDelMismoInstanteSeOrdenanPorIdDescendente() {
        // Con el mismo observadoEn desempata el id: el mayor primero. No promete orden de inserción.
        UUID a = pesoRepository.save(new Peso(MANANA_DIA_26, new BigDecimal("72.50"))).getId();
        UUID b = pesoRepository.save(new Peso(MANANA_DIA_26, new BigDecimal("72.60"))).getId();
        pesoRepository.flush();
        entityManager.clear();

        // PostgreSQL ordena los UUID byte a byte. Su texto (hexadecimal en minúsculas, longitud fija)
        // se ordena igual, así que comparamos el texto. No se usa UUID.compareTo: compara números
        // con signo y no garantiza el mismo orden que PostgreSQL.
        List<UUID> esperado = a.toString().compareTo(b.toString()) > 0 ? List.of(a, b) : List.of(b, a);

        List<UUID> lista = pesoRepository.findAllByOrderByObservadoEnDescIdDesc().stream().map(Peso::getId).toList();

        assertThat(lista).containsExactlyElementsOf(esperado);
    }

    @Test
    void laBaseDeDatosRechazaUnPesoAbsurdoAunqueNoPaseLaValidacionJava() {
        // Aquí no hay controller ni @Valid: se guarda directamente. Aun así, el CHECK de la tabla lo impide.
        Peso absurdo = new Peso(MANANA_DIA_26, new BigDecimal("600"));

        assertThatThrownBy(() -> pesoRepository.saveAndFlush(absurdo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
