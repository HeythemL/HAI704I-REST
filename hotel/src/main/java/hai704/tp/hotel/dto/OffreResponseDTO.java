package hai704.tp.hotel.dto;

import java.util.List;
import java.util.Map;

public record OffreResponseDTO(
        List<OffreDTO> offres,
        Map<String, LinkDTO> _links,
        long total
) {}