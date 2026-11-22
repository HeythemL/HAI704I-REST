package hai704.tp.demo.client;

import hai704.tp.demo.dto.OffreRequestDTO;
import hai704.tp.demo.dto.OffreResponseDTO;
import hai704.tp.demo.dto.OffreDTO;
import hai704.tp.demo.dto.ReservationRequestDTO;
import hai704.tp.demo.dto.ReservationResponseDTO;
import hai704.tp.demo.dto.LinkDTO;
import org.springframework.web.client.RestClient;

import java.util.Optional;

public class HotelApiClient {

    private final OffreClient offreClient;
    private final ReservationClient reservationClient;

    public HotelApiClient(RestClient restClient, String agencySecret) {
        this.offreClient = new OffreClient(restClient);
        this.reservationClient = new ReservationClient(restClient, agencySecret);
    }

    // === API Offres ===
    public OffreResponseDTO rechercherOffres(OffreRequestDTO criteria) {
        return offreClient.rechercher(criteria);
    }

    public Optional<OffreDTO> getOffre(String id) {
        return offreClient.getOffre(id);
    }

    public Optional<byte[]> getPhoto(String id) {
        return offreClient.getPhoto(id);
    }

    // === API Réservations ===
    public ReservationResponseDTO creerReservation(ReservationRequestDTO request, String idempotencyKey) {
        return reservationClient.creer(request, idempotencyKey);
    }

    public Optional<ReservationResponseDTO> getReservation(String id) {
        return reservationClient.getReservation(id);
    }

    public Optional<ReservationResponseDTO> modifierReservation(String id, ReservationRequestDTO request, String ifMatch) {
        return reservationClient.modifier(id, request, ifMatch);
    }

    public boolean annulerReservation(String id) {
        return reservationClient.annuler(id);
    }

    // === Navigation HATEOAS transversale ===
    public Optional<OffreDTO> suivreLienOffre(OffreResponseDTO response, String rel) {
        return offreClient.followLink(response, rel);
    }

    public Optional<ReservationResponseDTO> suivreLienReservation(ReservationResponseDTO reservation, String rel) {
        return reservationClient.followLink(reservation, rel);
    }

    // Workflow complet : rechercher → choisir → réserver
    public ReservationResponseDTO reserverPremiereOffreDisponible(OffreRequestDTO criteria, String nomClient, String idempotencyKey) {
        OffreResponseDTO response = rechercherOffres(criteria);
        if (response.offres().isEmpty()) {
            throw new IllegalStateException("Aucune offre disponible pour ces critères");
        }
        OffreDTO offre = response.offres().get(0);

        ReservationRequestDTO reservationRequest = new ReservationRequestDTO(
                offre.id(), nomClient, criteria.dateDebut(), criteria.dateFin()
        );
        return creerReservation(reservationRequest, idempotencyKey);
    }
}