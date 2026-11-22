package hai704.tp.hotel;

import hai704.tp.hotel.dto.ReservationRequestDTO;
import hai704.tp.hotel.dto.ReservationResponseDTO;
import hai704.tp.hotel.service.OffreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReservationA5IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OffreService offreService;

    private RestClient restClient;
    private static final String VALID_SECRET = "secret123";
    private static final String INVALID_SECRET = "wrongSecret";

    @BeforeEach
    void setUp() {
        offreService.initSampleData();
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // A5.1 — CRÉATION ET AUTHENTIFICATION
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("A5.1 — Création et Authentification")
    class CreationEtAuth {

        @Test
        @DisplayName("1. Succès : 201 Created avec en-têtes Location et ETag")
        void creerReservation_succes_201EtLocation() {
            ReservationRequestDTO dto = new ReservationRequestDTO(
                    "H1-1", "Dupont",
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)
            );

            ResponseEntity<ReservationResponseDTO> response = restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getLocation()).isNotNull();
            assertThat(response.getHeaders().getLocation().toString())
                    .startsWith("/api/v1/reservations/RES-");
            assertThat(response.getHeaders().getETag()).isNotNull();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isNew()).isTrue();
            assertThat(response.getBody().statut()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("2. Lecture de l'URI créée : GET /api/v1/reservations/{id} rend 200 OK")
        void lireReservation_apresCreation_200OK() {
            ReservationRequestDTO dto = new ReservationRequestDTO(
                    "H1-1", "Martin",
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)
            );

            ResponseEntity<ReservationResponseDTO> creation = restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            String location = creation.getHeaders().getLocation().toString();

            ResponseEntity<ReservationResponseDTO> lecture = restClient.get()
                    .uri(location)
                    .header("X-Agency-Secret", VALID_SECRET)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            assertThat(lecture.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(lecture.getBody().id()).isEqualTo(creation.getBody().id());
            assertThat(lecture.getBody().nomClient()).isEqualTo("Martin");
            assertThat(lecture.getHeaders().getETag()).isNotNull();
        }

        @Test
        @DisplayName("3. Authentification : Agence inconnue refusée avec 401 et WWW-Authenticate")
        void agenceInconnue_refusee_401AvecWWWAuthenticate() {
            ReservationRequestDTO dto = new ReservationRequestDTO(
                    "H1-1", "Hacker",
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)
            );

            assertThatThrownBy(() -> restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", INVALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto)
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class)
                    .satisfies(ex -> {
                        HttpClientErrorException u = (HttpClientErrorException) ex;
                        assertThat(u.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(u.getResponseHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNotEmpty();
                        assertThat(u.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                                .contains("X-Agency-Secret");
                    });
        }

        @Test
        @DisplayName("4. Offre inexistante rend 404 Not Found")
        void reserverOffreInexistante_rend404() {
            ReservationRequestDTO dto = new ReservationRequestDTO(
                    "OFFRE-FANTOME", "Client",
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)
            );

            assertThatThrownBy(() -> restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto)
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        }

        @Test
        @DisplayName("5. Offre déjà prise rend 409 Conflict")
        void reserverOffreDejaPrise_rend409() {
            ReservationRequestDTO dto1 = new ReservationRequestDTO(
                    "H1-1", "Client1",
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)
            );
            restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto1)
                    .retrieve()
                    .toBodilessEntity();

            // Même offre, dates chevauchantes
            ReservationRequestDTO dto2 = new ReservationRequestDTO(
                    "H1-1", "Client2",
                    LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 8)
            );

            assertThatThrownBy(() -> restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto2)
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Conflict.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // A5.2 — IDEMPOTENCE DU REJEU (Idempotency-Key)
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("A5.2 — Idempotence du rejeu")
    class Idempotence {

        @Test
        @DisplayName("Deux demandes avec la même clé : 1er appel 201 Created, 2e appel 200 OK")
        void rejeuMemeCle_rend200SansDoublon() {
            String idemKey = "cle-idempotence-unique-42";
            ReservationRequestDTO dto = new ReservationRequestDTO(
                    "H1-2", "Voyageur",
                    LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 15)
            );

            // Premier appel -> 201 Created
            ResponseEntity<ReservationResponseDTO> rep1 = restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .header("Idempotency-Key", idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            assertThat(rep1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(rep1.getBody().isNew()).isTrue();
            String originalId = rep1.getBody().id();

            // Rejeu avec la même clé -> 200 OK (pas de seconde création)
            ResponseEntity<ReservationResponseDTO> rep2 = restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .header("Idempotency-Key", idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(dto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            assertThat(rep2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(rep2.getBody().isNew()).isFalse();
            assertThat(rep2.getBody().id()).isEqualTo(originalId);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // A5.3 — CONCURRENCE OPTIMISTE (ETag) ET ANNULATION IDEMPOTENTE
    // ═════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("A5.3 — Concurrence Optimiste et Annulation")
    class ConcurrenceEtAnnulation {

        @Test
        @DisplayName("1. Modification avec ETag valide : 200 OK et ETag mis à jour")
        void modifierAvecETagValide_200OK() {
            ReservationRequestDTO createDto = new ReservationRequestDTO(
                    "H2-1", "Jean",
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4)
            );
            ResponseEntity<ReservationResponseDTO> created = restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createDto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            String resId = created.getBody().id();
            String etagInitial = created.getHeaders().getETag();
            assertThat(etagInitial).isNotNull();

            ReservationRequestDTO updateDto = new ReservationRequestDTO(
                    "H2-1", "Jean Modifié",
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4)
            );

            ResponseEntity<ReservationResponseDTO> updated = restClient.put()
                    .uri("/api/v1/reservations/" + resId)
                    .header("X-Agency-Secret", VALID_SECRET)
                    .header("If-Match", etagInitial)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updateDto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody().nomClient()).isEqualTo("Jean Modifié");
        }

        @Test
        @DisplayName("2. Modification avec ETag périmé : 412 Precondition Failed")
        void modifierAvecETagPerime_rend412() {
            ReservationRequestDTO createDto = new ReservationRequestDTO(
                    "H2-1", "Claire",
                    LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14)
            );
            ResponseEntity<ReservationResponseDTO> created = restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createDto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            String resId = created.getBody().id();

            ReservationRequestDTO updateDto = new ReservationRequestDTO(
                    "H2-1", "Claire Concurrente",
                    LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 14)
            );

            assertThatThrownBy(() -> restClient.put()
                    .uri("/api/v1/reservations/" + resId)
                    .header("X-Agency-Secret", VALID_SECRET)
                    .header("If-Match", "\"ETAG-PERIME-999\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updateDto)
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.class)
                    .satisfies(ex -> {
                        HttpClientErrorException hce = (HttpClientErrorException) ex;
                        assertThat(hce.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                    });
        }

        @Test
        @DisplayName("3. Modification sans If-Match : 428 Precondition Required")
        void modifierSansIfMatch_rend428() {
            ReservationRequestDTO createDto = new ReservationRequestDTO(
                    "H2-1", "Paul",
                    LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 24)
            );
            ResponseEntity<ReservationResponseDTO> created = restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createDto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            String resId = created.getBody().id();

            ReservationRequestDTO updateDto = new ReservationRequestDTO(
                    "H2-1", "Paul Sans Header",
                    LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 24)
            );

            assertThatThrownBy(() -> restClient.put()
                    .uri("/api/v1/reservations/" + resId)
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updateDto)
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.class)
                    .satisfies(ex -> {
                        HttpClientErrorException hce = (HttpClientErrorException) ex;
                        assertThat(hce.getStatusCode().value()).isEqualTo(428);
                    });
        }

        @Test
        @DisplayName("4. Annulation (DELETE) : 204 No Content, répétable avec même effet")
        void annulerReservation_204EtIdempotent() {
            ReservationRequestDTO createDto = new ReservationRequestDTO(
                    "H1-2", "Annulable",
                    LocalDate.of(2026, 12, 26), LocalDate.of(2026, 12, 30)
            );
            ResponseEntity<ReservationResponseDTO> created = restClient.post()
                    .uri("/api/v1/reservations")
                    .header("X-Agency-Secret", VALID_SECRET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createDto)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);

            String resId = created.getBody().id();

            // 1er DELETE -> 204 No Content
            ResponseEntity<Void> del1 = restClient.delete()
                    .uri("/api/v1/reservations/" + resId)
                    .header("X-Agency-Secret", VALID_SECRET)
                    .retrieve()
                    .toBodilessEntity();
            assertThat(del1.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Vérifier statut CANCELLED
            ResponseEntity<ReservationResponseDTO> verif = restClient.get()
                    .uri("/api/v1/reservations/" + resId)
                    .header("X-Agency-Secret", VALID_SECRET)
                    .retrieve()
                    .toEntity(ReservationResponseDTO.class);
            assertThat(verif.getBody().statut()).isEqualTo("CANCELLED");

            // 2e DELETE (rejeu idempotent) -> encore 204 No Content
            ResponseEntity<Void> del2 = restClient.delete()
                    .uri("/api/v1/reservations/" + resId)
                    .header("X-Agency-Secret", VALID_SECRET)
                    .retrieve()
                    .toBodilessEntity();
            assertThat(del2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
