package hai704.tp.demo.dto;

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