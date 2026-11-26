package bj.repetipro.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessionActiveInterceptor sessionActive;

    public WebConfig(SessionActiveInterceptor sessionActive) { this.sessionActive = sessionActive; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionActive).excludePathPatterns("/css/**", "/favicon.svg", "/connexion", "/deconnexion", "/error");
    }
}
