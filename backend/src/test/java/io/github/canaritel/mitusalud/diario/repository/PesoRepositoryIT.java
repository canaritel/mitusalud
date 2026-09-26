package io.github.canaritel.mitusalud.diario.repository;

import io.github.canaritel.mitusalud.diario.entity.Peso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
@ActiveProfiles("test") // carga application-test.properties: la base mitusalud_test
class PesoRepositoryIT {

    @Autowired
    private PesoRepository pesoRepository;

    @Test
    void guardaLosKilosConDecimalesExactos() {
        Peso guardado = pesoRepository.saveAndFlush(new Peso(LocalDate.of(2026, 9, 26), new BigDecimal("72.35")));

        assertThat(guardado.getId()).isNotNull(); // lo asignó PostgreSQL
        Peso leido = pesoRepository.findById(guardado.getId()).orElseThrow();
        // compareTo y no equals: para BigDecimal, 72.35 y 72.350 son iguales en valor pero no en equals.
        assertThat(leido.getKilos()).isEqualByComparingTo("72.35");
    }

    @Test
    void listaDelMasRecienteAlMasAntiguo() {
        Peso antiguo = pesoRepository.save(new Peso(LocalDate.of(2026, 9, 24), new BigDecimal("73.00")));
        Peso mananaDelDia26 = pesoRepository.save(new Peso(LocalDate.of(2026, 9, 26), new BigDecimal("72.50")));
        Peso nocheDelDia26 = pesoRepository.save(new Peso(LocalDate.of(2026, 9, 26), new BigDecimal("72.90")));

        List<Peso> lista = pesoRepository.findAllByOrderByFechaDescIdDesc();

        // Mismo día: primero el último registrado (id mayor).
        assertThat(lista).extracting(Peso::getId)
                .containsExactly(nocheDelDia26.getId(), mananaDelDia26.getId(), antiguo.getId());
    }

    @Test
    void laBaseDeDatosRechazaUnPesoAbsurdoAunqueNoPaseLaValidacionJava() {
        // Aquí no hay controller ni @Valid: se guarda directamente. Aun así, el CHECK de la tabla lo impide.
        Peso absurdo = new Peso(LocalDate.of(2026, 9, 26), new BigDecimal("600"));

        assertThatThrownBy(() -> pesoRepository.saveAndFlush(absurdo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
