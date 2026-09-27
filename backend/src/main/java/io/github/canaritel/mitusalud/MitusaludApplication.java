package io.github.canaritel.mitusalud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MitusaludApplication {

    public static void main(String[] args) {
        SpringApplication aplicacion = new SpringApplication(MitusaludApplication.class);

        // "acceso primero" o "acceso recuperar": comando administrativo (acceso/ComandoAcceso).
        // Arranca sin servidor web, para no chocar con el backend en marcha, hace su trabajo y termina.
        boolean comando = args.length > 0 && "acceso".equals(args[0]);
        if (comando) {
            aplicacion.setWebApplicationType(WebApplicationType.NONE);
        }

        ConfigurableApplicationContext contexto = aplicacion.run(args);
        if (comando) {
            System.exit(SpringApplication.exit(contexto));
        }
    }

}
