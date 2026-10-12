package za.co.petplace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class PetPlaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetPlaceApplication.class, args);
    }

    /** Lets the site call /api when it is opened from a different local port. */
    @Bean
    WebMvcConfigurer corsConfigurer(@Value("${petplace.cors-origins}") String[] origins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST");
            }
        };
    }
}
