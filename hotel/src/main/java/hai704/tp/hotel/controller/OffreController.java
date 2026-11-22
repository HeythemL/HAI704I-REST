package hai704.tp.hotel.controller;

import hai704.tp.hotel.dto.OffreRequestDTO;
import hai704.tp.hotel.dto.OffreResponseDTO;
import hai704.tp.hotel.exception.InvalidMediaTypeException;
import hai704.tp.hotel.exception.OffreNotFoundException;
import hai704.tp.hotel.service.OffreService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/offres")
public class OffreController {

    private final OffreService offreService;

    public OffreController(OffreService offreService) {
        this.offreService = offreService;
    }

    @GetMapping
    public ResponseEntity<OffreResponseDTO> rechercher(
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) Integer nbPersonnes) {

        LocalDate parsedDateDebut = parseDate(dateDebut);
        LocalDate parsedDateFin = parseDate(dateFin);

        OffreRequestDTO criteria = new OffreRequestDTO(ville, parsedDateDebut, parsedDateFin, nbPersonnes);
        OffreResponseDTO response = offreService.rechercher(criteria);
        return ResponseEntity.ok(response);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Format de date invalide: " + dateStr + " (attendu: YYYY-MM-DD)");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<OffreResponseDTO> findById(@PathVariable String id) {
        Optional<OffreResponseDTO> response = offreService.findByIdAsDTO(id);
        if (response.isEmpty()) {
            throw new OffreNotFoundException(id);
        }
        return ResponseEntity.ok(response.get());
    }

    @GetMapping(value = "/{id}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getPhoto(@PathVariable String id) {
        Optional<byte[]> photo = offreService.getPhoto(id);
        if (photo.isEmpty()) {
            throw new OffreNotFoundException(id);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(photo.get());
    }
}