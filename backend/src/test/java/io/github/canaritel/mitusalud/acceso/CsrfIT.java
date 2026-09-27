package io.github.canaritel.mitusalud.acceso;

import io.github.canaritel.mitusalud.UsaBaseDeDatosDeTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La protección CSRF de verdad, con un servidor real y un cliente HTTP: así lo hará la pantalla React.
 * El token llega en la cookie XSRF-TOKEN y vuelve en la cabecera X-XSRF-TOKEN.
 *
 * No usa MockMvc a propósito: with(csrf()), que usan otros tests, sustituye el almacén de tokens del
 * contexto de pruebas compartido y cambiaría lo que se prueba aquí.
 *
 * Requisitos: túnel SSH abierto y "./mvnw verify -Pbd".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@UsaBaseDeDatosDeTest
class CsrfIT {

    @LocalServerPort
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void elTokenDeLaCookieSoloValeSiVuelveEnLaCabecera() throws Exception {
        HttpResponse<String> pagina = http.send(peticion("/login/ott").GET().build(), HttpResponse.BodyHandlers.ofString());
        String token = pagina.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .map(cookie -> cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';')))
                .findFirst().orElseThrow();

        assertThat(opcionesDeEntrada(token, token)).isEqualTo(200);
        assertThat(opcionesDeEntrada(token, null)).isEqualTo(403);
        assertThat(opcionesDeEntrada(token, "otro-valor")).isEqualTo(403);
    }

    // POST /webauthn/authenticate/options: no necesita sesión, pero sí el token CSRF.
    private int opcionesDeEntrada(String cookie, String cabecera) throws Exception {
        HttpRequest.Builder peticion = peticion("/webauthn/authenticate/options")
                .header("Cookie", "XSRF-TOKEN=" + cookie)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (cabecera != null) {
            peticion.header("X-XSRF-TOKEN", cabecera);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpRequest.Builder peticion(String ruta) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
    }
}
