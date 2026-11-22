package hai704.tp.demo.dto;

import java.util.Map;

public record ReservationResponseDTO(
        String id,
        String offreId,
        String nomClient,
        String dateDebut,
        String dateFin,
        String statut,
        Double prixPaye,
        Map<String, LinkDTO> _links,
        boolean isNew,
        String etag
) {
    // Constructeur de compatibilité sans etag
    public ReservationResponseDTO(
            String id,
            String offreId,
            String nomClient,
            String dateDebut,
            String dateFin,
            String statut,
            Double prixPaye,
            Map<String, LinkDTO> _links,
            boolean isNew) {
        this(id, offreId, nomClient, dateDebut, dateFin, statut, prixPaye, _links, isNew, null);
    }
}