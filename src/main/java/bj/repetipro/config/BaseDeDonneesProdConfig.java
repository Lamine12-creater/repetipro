package bj.repetipro.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Connexion à PostgreSQL (Neon) en production (profil "prod").
 *
 * Variable d'environnement DATABASE_URL, au choix :
 *  - format Neon tel quel :   postgresql://utilisateur:motdepasse@hote/base?sslmode=require
 *  - ou format JDBC :          jdbc:postgresql://hote/base?sslmode=require
 *    (avec alors DATABASE_USERNAME et DATABASE_PASSWORD)
 */
@Configuration
@Profile("prod")
public class BaseDeDonneesProdConfig {

    @Bean
    public DataSource dataSource(Environment env) {
        String url = env.getRequiredProperty("DATABASE_URL");
        String utilisateur = env.getProperty("DATABASE_USERNAME");
        String motDePasse = env.getProperty("DATABASE_PASSWORD");

        String jdbcUrl;
        if (url.startsWith("jdbc:")) {
            jdbcUrl = url;
        } else {
            // On "démonte" l'URL Neon pour la transformer en URL JDBC.
            URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "http://"));
            if (uri.getUserInfo() != null) {
                String[] infos = uri.getUserInfo().split(":", 2);
                utilisateur = java.net.URLDecoder.decode(infos[0], java.nio.charset.StandardCharsets.UTF_8);
                if (infos.length > 1) motDePasse = java.net.URLDecoder.decode(infos[1], java.nio.charset.StandardCharsets.UTF_8);
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            String requete = uri.getQuery() == null ? "sslmode=require" : uri.getQuery();
            // Neon ajoute parfois "channel_binding=require", que le pilote JDBC accepte aussi.
            jdbcUrl = "jdbc:postgresql://" + uri.getHost() + port + uri.getPath() + "?" + requete;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(utilisateur);
        config.setPassword(motDePasse);
        config.setMaximumPoolSize(3);   // l'offre gratuite de Neon limite les connexions : on reste sobre
        return new HikariDataSource(config);
    }
}
