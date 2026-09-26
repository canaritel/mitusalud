package io.github.canaritel.mitusalud;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un test que usa la base de datos de test. Agrupa las dos protecciones
 * para que ningún test pueda llevar una y olvidar la otra:
 *
 * 1. @TestPropertySource carga bd-test.properties con más prioridad que las variables de entorno,
 *    así que nada externo puede cambiar la base de datos de los tests.
 * 2. GuardiaBaseDeTest comprueba, antes de Flyway, que la conexión es realmente a mitusalud_test.
 *
 * Uso: poner @UsaBaseDeDatosDeTest en la clase del test (junto a @DataJpaTest o @SpringBootTest).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TestPropertySource(locations = "classpath:bd-test.properties")
@Import(GuardiaBaseDeTest.class)
public @interface UsaBaseDeDatosDeTest {
}
