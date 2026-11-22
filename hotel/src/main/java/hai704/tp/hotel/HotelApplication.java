package hai704.tp.hotel;

import hai704.tp.hotel.service.OffreService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HotelApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelApplication.class, args);
    }

    @Bean
    CommandLineRunner initData(OffreService offreService) {
        return args -> {
            offreService.initSampleData();
        };
    }
}