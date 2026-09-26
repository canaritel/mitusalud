package io.github.canaritel.mitusalud.diario;

import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import io.github.canaritel.mitusalud.diario.dto.PesoEntrada;
import io.github.canaritel.mitusalud.diario.service.PesoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba la regla de la pieza 1 (docs/MODELO_DATOS.md): la observación y su detalle se guardan
 * en la misma transacción. Si falla el detalle, tampoco debe quedar la observación.
 *
 * A propósito, este test NO lleva @Transactional: así se usa la transacción real del service,
 * que se confirma o se deshace sola, igual que en producción. Después se cuenta con SQL directo
 * sobre la tabla observation, sin pasar por las entidades, para que no se escape una fila padre
 * sin detalle.
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@SpringBootTest
@UsaBaseDeDatosDeTest
class PesoTransaccionIT {

    @Autowired
    private PesoService pesoService;

    @Autowired
    private JdbcTemplate jdbcTemplate; // consultas SQL directas, cada una en su propia conexión

    @Test
    void siFallaElDetalleNoQuedaLaObservacion() {
        long observacionesAntes = contar("observation");
        long pesosAntes = contar("peso");

        // Se llama al service directamente, sin pasar por la validación del controller:
        // 600 kg llega a la base de datos y el CHECK de la tabla peso lo rechaza.
        PesoEntrada absurdo = new PesoEntrada(OffsetDateTime.parse("2026-09-26T08:30:00+02:00"), new BigDecimal("600"));
        // No basta con "falla": tiene que fallar por el CHECK de los kilos en PostgreSQL. Si fallara antes
        // por otro motivo (un NullPointerException, por ejemplo), no demostraría nada sobre el rollback.
        // SQLState 23514 = violación de una restricción CHECK (código estándar de SQL).
        assertThatThrownBy(() -> pesoService.registrar(absurdo))
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, causa -> {
                    assertThat(causa.getSQLState()).isEqualTo("23514");
                    assertThat(causa.getMessage()).contains("peso_kilos_check");
                });

        // La transacción del service ya terminó (con rollback). Ni observation ni peso han cambiado.
        assertThat(contar("observation")).isEqualTo(observacionesAntes);
        assertThat(contar("peso")).isEqualTo(pesosAntes);
    }

    private long contar(String tabla) {
        // El nombre de la tabla es fijo en este test, nunca viene de fuera: no hay riesgo de inyección SQL.
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + tabla, Long.class);
    }
}
