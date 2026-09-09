package es.jaes.cn_servicies.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enciende el planificador de tareas.
 *
 * <p>En una clase aparte y no en la aplicacion principal para que se pueda
 * apagar entero con {@code app.sessions.generation.enabled=false}. Eso hace
 * falta el dia que haya <b>mas de una instancia</b>: hoy dos replicas generando
 * a la vez no duplicarian nada —la generacion es idempotente y el indice unico
 * es la red— pero harian el mismo trabajo dos veces, y lo razonable es que solo
 * una lo haga.
 *
 * <p>No se apaga en los tests, y no hace falta: el cron es de madrugada y
 * ninguna suite dura lo suficiente para que salte. Los tests del job llaman a su
 * metodo directamente, que es como hay que probarlo — un test que dependiera del
 * reloj no seria un test.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.sessions.generation.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfig {
}
