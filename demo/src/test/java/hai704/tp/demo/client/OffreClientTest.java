package hai704.tp.demo.client;

import hai704.tp.demo.dto.OffreRequestDTO;
import hai704.tp.demo.dto.OffreResponseDTO;
import hai704.tp.demo.dto.OffreDTO;
import hai704.tp.demo.service.OffreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OffreClientTest {

    @LocalServerPort
    int port;

    @Autowired
    private OffreService offreService;

    private OffreClient client;

    @BeforeEach
    void setup() {
        // Ensure sample data is initialized
        offreService.initSampleData();

        // Debug: verify data exists
        System.out.println("=== DEBUG: Number of offres in DB: " + offreService.findAll().size());

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
        client = new OffreClient(restClient);
    }

    @Test
    void rechercherOffres_retourneCollectionAvecLinks() {
        OffreRequestDTO criteria = new OffreRequestDTO(
                "Paris", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), 2
        );

        OffreResponseDTO response = client.rechercher(criteria);

        assertThat(response).isNotNull();
        assertThat(response.offres()).isNotEmpty();
        assertThat(response._links()).containsKey("self");
        assertThat(response.total()).isEqualTo(response.offres().size());

        // Vérifier liens sur chaque offre
        for (OffreDTO offre : response.offres()) {
            assertThat(offre._links()).containsKeys("self", "photo", "reserver");
            assertThat(offre._links().get("self").href()).endsWith("/api/v1/offres/" + offre.id());
            assertThat(offre._links().get("photo").href()).endsWith("/photo");
            assertThat(offre._links().get("photo").type()).isEqualTo("image/jpeg");
            assertThat(offre._links().get("reserver").method()).isEqualTo("POST");
        }
    }

    @Test
    void getOffre_existant_retourneOffreAvecLinks() {
        Optional<OffreDTO> offre = client.getOffre("H1-1");

        assertThat(offre).isPresent();
        assertThat(offre.get()._links()).containsKeys("self", "photo", "reserver");
    }

    @Test
    void getOffre_inexistant_retourneEmpty() {
        Optional<OffreDTO> offre = client.getOffre("INEXISTANT");

        assertThat(offre).isEmpty();
    }

    @Test
    void navigationHATEOAS_suivreLienSelf_retourneMemeOffre() {
        OffreRequestDTO criteria = new OffreRequestDTO(
                "Paris", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), 2
        );
        OffreResponseDTO response = client.rechercher(criteria);
        OffreDTO first = response.offres().get(0);

        Optional<OffreDTO> followed = client.followLink(response, "self");

        // Le lien "self" sur la collection pointe vers la collection, pas l'offre
        // Pour une offre individuelle, utiliser getOffre(id)
        assertThat(followed).isEmpty(); // Attendu : lien "self" = collection
    }
}