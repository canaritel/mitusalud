package io.github.canaritel.mitusalud;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Comprueba las fronteras del monolito modular.
 * Cada paquete directo bajo io.github.canaritel.mitusalud (diario, ingesta...) es un módulo.
 * El test falla si un módulo usa clases internas de otro o si hay dependencias circulares.
 * No necesita base de datos: solo analiza el código compilado.
 */
class ModularidadTest {

    @Test
    void losModulosRespetanSusFronteras() {
        ApplicationModules.of(MitusaludApplication.class).verify();
    }
}
