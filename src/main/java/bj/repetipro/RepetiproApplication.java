package bj.repetipro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Point d'entrée de l'application. Lancez cette classe (ou "mvn spring-boot:run").
 */
@SpringBootApplication
public class RepetiproApplication {

    public static void main(String[] args) {
        // Les serveurs (Render) sont en heure UTC : on force l'heure du Bénin (UTC+1)
        // pour que "aujourd'hui" et les heures des séances soient justes.
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Porto-Novo"));
        SpringApplication.run(RepetiproApplication.class, args);
    }
}
