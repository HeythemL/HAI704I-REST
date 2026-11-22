package hai704.tp.demo.controller;

import hai704.tp.demo.dto.OffreRequestDTO;
import hai704.tp.demo.dto.OffreResponseDTO;
import hai704.tp.demo.exception.OffreNotFoundException;
import hai704.tp.demo.service.OffreService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offres")
public class OffreController {

    private final OffreService offreService;

    public OffreController(OffreService offreService) {
        this.offreService = offreService;
    }

    // GET /api/v1/offres?ville=...&dateDebut=...&dateFin=...&nbPersonnes=...
    @GetMapping
    public ResponseEntity<OffreResponseDTO> rechercherOffres(@Valid OffreRequestDTO criteria) {
        if (!criteria.isDateRangeValid()) {
            throw new IllegalArgumentException("La date de début doit être antérieure ou égale à la date de fin");
        }
        return ResponseEntity.ok(offreService.rechercher(criteria));
    }

    // GET /api/v1/offres/{id}
    @GetMapping("/{id}")
    public ResponseEntity<OffreResponseDTO> getOffre(@PathVariable String id) {
        return offreService.findByIdAsDTO(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new OffreNotFoundException(id));
    }

    // GET /api/v1/offres/{id}/photo
    @GetMapping(value = "/{id}/photo", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> getPhoto(@PathVariable String id) {
        return offreService.getPhoto(id)
                .map(photo -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .header("Content-Length", String.valueOf(photo.length))
                        .header("ETag", "\"photo-" + id + "\"")
                        .body(photo))
                .orElseThrow(() -> new OffreNotFoundException(id));
    }
}