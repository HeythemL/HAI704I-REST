package hai704.tp.hotel.dto;

import java.util.Map;

public record OffreDTO(
        String id,
        String nomHotel,
        String ville,
        String typeChambre,
        Integer capacite,
        Double prixTotal,
        String dateDebut,
        String dateFin,
        Map<String, LinkDTO> _links
) {}