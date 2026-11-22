package hai704.tp.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProblemDetailConfig implements WebMvcConfigurer {
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        // Force ProblemDetail (application/problem+json) comme format d'erreur par défaut
        configurer.defaultContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .mediaType("problem", MediaType.APPLICATION_PROBLEM_JSON);
    }
}
