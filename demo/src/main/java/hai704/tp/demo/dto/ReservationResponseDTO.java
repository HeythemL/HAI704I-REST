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
        boolean isNew
) {}