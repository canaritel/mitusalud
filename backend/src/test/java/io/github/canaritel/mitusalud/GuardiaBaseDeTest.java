package io.github.canaritel.mitusalud;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Última defensa de los tests con base de datos: antes de que Flyway toque nada,
 * pregunta a PostgreSQL a qué base están conectados. Si no es la de test, detiene los tests.
 *
 * Comprueba las DOS conexiones, porque Spring permite que sean distintas (spring.flyway.url):
 * - la de Flyway, que crea y modifica las tablas;
 * - la de la aplicación (JPA), que leen y escriben los tests.
 *
 * Protege de errores humanos, como editar bd-test.properties y apuntar a otra base por despiste.
 */
@TestConfiguration
public class GuardiaBaseDeTest {

    static final String BASE_PERMITIDA = "mitusalud_test";

    // Spring Boot llama a este bean en lugar de ejecutar Flyway directamente.
    // El parámetro dataSource es la conexión que usa JPA; Spring lo inyecta.
    @Bean
    FlywayMigrationStrategy migrarSoloLaBaseDeTest(DataSource dataSource) {
        return flyway -> {
            comprobar("Flyway", flyway.getConfiguration().getDataSource());
            comprobar("JPA", dataSource);
            flyway.migrate();
        };
    }

    private static void comprobar(String quien, DataSource conexiones) {
        String baseConectada;
        try (Connection conexion = conexiones.getConnection()) {
            // En PostgreSQL, el "catalog" de la conexión es el nombre de la base de datos.
            baseConectada = conexion.getCatalog();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo comprobar a qué base de datos está conectado " + quien, e);
        }
        if (!BASE_PERMITIDA.equals(baseConectada)) {
            throw new IllegalStateException("Los tests solo pueden usar la base '" + BASE_PERMITIDA + "', pero "
                    + quien + " está conectado a '" + baseConectada + "'. No se ha modificado nada.");
        }
    }
}
