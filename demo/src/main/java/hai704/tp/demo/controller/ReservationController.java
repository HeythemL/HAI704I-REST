package hai704.tp.demo.controller;

import hai704.tp.demo.dto.ReservationRequestDTO;
import hai704.tp.demo.dto.ReservationResponseDTO;
import hai704.tp.demo.exception.OffreNotFoundException;
import hai704.tp.demo.exception.ReservationNotFoundException;
import hai704.tp.demo.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // POST /api/v1/reservations
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationResponseDTO> creerReservation(
            @Valid @RequestBody ReservationRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret) {

        if (!reservationService.isValidAgencySecret(agencySecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "X-Agency-Secret realm=\"Hotel API\"")
                    .build();
        }

        var result = reservationService.creer(request, idempotencyKey);

        HttpHeaders headers = new HttpHeaders();
        if (result.reservation().etag() != null) {
            headers.set("ETag", result.reservation().etag());
        }
        headers.setLocation(URI.create("/api/v1/reservations/" + result.reservation().id()));

        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;

        return new ResponseEntity<>(result.reservation(), headers, status);
    }

    // GET /api/v1/reservations/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponseDTO> getReservation(
            @PathVariable String id,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret) {

        if (!reservationService.isValidAgencySecret(agencySecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "X-Agency-Secret realm=\"Hotel API\"")
                    .build();
        }

        return reservationService.findById(id)
                .map(res -> {
                    HttpHeaders headers = new HttpHeaders();
                    if (res.etag() != null) {
                        headers.set("ETag", res.etag());
                    }
                    return new ResponseEntity<>(res, headers, HttpStatus.OK);
                })
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    // PUT /api/v1/reservations/{id} (concurrence optimiste avec ETag)
    @PutMapping("/{id}")
    public ResponseEntity<ReservationResponseDTO> modifierReservation(
            @PathVariable String id,
            @Valid @RequestBody ReservationRequestDTO request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret) {

        if (!reservationService.isValidAgencySecret(agencySecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "X-Agency-Secret realm=\"Hotel API\"")
                    .build();
        }

        if (ifMatch == null || ifMatch.isBlank()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).build();
        }

        return reservationService.modifier(id, request, ifMatch)
                .map(res -> {
                    HttpHeaders headers = new HttpHeaders();
                    if (res.etag() != null) {
                        headers.set("ETag", res.etag());
                    }
                    return ResponseEntity.ok().headers(headers).body(res);
                })
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    // DELETE /api/v1/reservations/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> annulerReservation(
            @PathVariable String id,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret) {

        if (!reservationService.isValidAgencySecret(agencySecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "X-Agency-Secret realm=\"Hotel API\"")
                    .build();
        }

        boolean deleted = reservationService.annuler(id);
        if (!deleted) {
            throw new ReservationNotFoundException(id);
        }
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}