package io.github.canaritel.mitusalud;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Última defensa de los tests con base de datos: antes de que Flyway toque nada,
 * pregunta a PostgreSQL a qué base está conectado. Si no es la de test, detiene los tests.
 *
 * Protege de errores humanos, como editar bd-test.properties y apuntar a otra base por despiste.
 */
@TestConfiguration
public class GuardiaBaseDeTest {

    static final String BASE_PERMITIDA = "mitusalud_test";

    // Spring Boot llama a este bean en lugar de ejecutar Flyway directamente.
    @Bean
    FlywayMigrationStrategy migrarSoloLaBaseDeTest() {
        return flyway -> {
            String baseConectada;
            try (Connection conexion = flyway.getConfiguration().getDataSource().getConnection()) {
                // En PostgreSQL, el "catalog" de la conexión es el nombre de la base de datos.
                baseConectada = conexion.getCatalog();
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo comprobar a qué base de datos están conectados los tests", e);
            }
            if (!BASE_PERMITIDA.equals(baseConectada)) {
                throw new IllegalStateException("Los tests solo pueden usar la base '" + BASE_PERMITIDA
                        + "', pero están conectados a '" + baseConectada + "'. No se ha modificado nada.");
            }
            flyway.migrate();
        };
    }
}
