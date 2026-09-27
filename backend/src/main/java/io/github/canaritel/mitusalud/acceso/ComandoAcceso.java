package io.github.canaritel.mitusalud.acceso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Comando administrativo, solo para quien tiene acceso a la máquina del backend y a su .env:
 *
 *   acceso primero     primera configuración: enlace para registrar la primera passkey
 *   acceso recuperar   ninguna passkey funciona: revoca todas, cierra las sesiones y da un enlace nuevo
 *
 * En desarrollo: ./mvnw spring-boot:run -Dspring-boot.run.arguments="acceso primero"
 * Arranca sin servidor web (ver MitusaludApplication), hace su trabajo y termina.
 */
@Component
class ComandoAcceso implements ApplicationRunner, ExitCodeGenerator {

    private final CuentaPropietario cuenta;
    private final String origen;
    private int codigoDeSalida;

    ComandoAcceso(CuentaPropietario cuenta, @Value("${mitusalud.acceso.origen}") String origen) {
        this.cuenta = cuenta;
        this.origen = origen;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        List<String> palabras = argumentos.getNonOptionArgs();
        if (palabras.isEmpty() || !"acceso".equals(palabras.getFirst())) {
            return; // arranque normal de la aplicación
        }
        String modo = palabras.size() > 1 ? palabras.get(1) : "";
        try {
            String token = switch (modo) {
                case "primero" -> cuenta.primerAcceso();
                case "recuperar" -> cuenta.recuperar();
                default -> throw new IllegalArgumentException("Uso: acceso primero | acceso recuperar");
            };
            // System.out y no el logger: el enlace se muestra en la terminal y nunca queda en los logs.
            // El token va tras "#", que el navegador no envía al servidor ni a ningún proxy.
            System.out.printf("""

                    Enlace de un solo uso, válido %d minutos:
                      %s/acceso#token=%s
                    Mientras la pantalla no esté lista, pega este token en %s/login/ott:
                      %s

                    """, CuentaPropietario.VIGENCIA_ENLACE.toMinutes(), origen, token, origen, token);
        } catch (IllegalStateException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
            codigoDeSalida = 1;
        }
    }

    @Override
    public int getExitCode() {
        return codigoDeSalida;
    }
}
