package io.github.canaritel.mitusalud.comun;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Idiomas de la API: español e inglés.
 *
 * El idioma de cada respuesta sale de la cabecera Accept-Language que envía el navegador.
 * Si pide un idioma no admitido (por ejemplo, francés) o no envía ninguno, se responde en español.
 * Sin esta lista, Spring aceptaría cualquier idioma y mezclaría textos propios en español
 * con mensajes de validación en el idioma pedido.
 */
@Configuration
public class ConfiguracionIdioma {

    private static final Locale ESPANOL = Locale.of("es");
    private static final Locale INGLES = Locale.ENGLISH;

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(ESPANOL, INGLES));
        resolver.setDefaultLocale(ESPANOL);
        return resolver;
    }
}
