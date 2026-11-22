package hai704.tp.hotel.controller;

import hai704.tp.hotel.dto.ReservationRequestDTO;
import hai704.tp.hotel.dto.ReservationResponseDTO;
import hai704.tp.hotel.exception.InvalidMediaTypeException;
import hai704.tp.hotel.exception.OptimisticLockingException;
import hai704.tp.hotel.exception.ReservationConflictException;
import hai704.tp.hotel.exception.ReservationNotFoundException;
import hai704.tp.hotel.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // ── Helper: authenticate the calling agency ───────────────────────────
    // A5.1-3 — An unknown agency is refused with 401 + WWW-Authenticate
    private ResponseEntity<?> checkAuth(String agencySecret) {
        if (!reservationService.isValidAgencySecret(agencySecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE,
                            "X-Agency-Secret realm=\"Hotel API\"")
                    .build();
        }
        return null; // auth OK
    }

    private void validateContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            throw new InvalidMediaTypeException(MediaType.APPLICATION_JSON_VALUE, contentType);
        }
    }

    // ── A5.1 — Create a reservation ─────────────────────────────────────
    // POST → 201 Created + Location header
    // Idempotency-Key replay → 200 OK (no second creation)
    // Missing/bad X-Agency-Secret → 401 + WWW-Authenticate
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> creer(
            @Valid @RequestBody ReservationRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret,
            HttpServletRequest httpRequest) {

        // A5.1-3 — Authentication
        ResponseEntity<?> authFail = checkAuth(agencySecret);
        if (authFail != null) return authFail;

        validateContentType(httpRequest);

        ReservationService.ReservationResult result = reservationService.creer(request, idempotencyKey);

        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", result.reservation().etag());
        headers.setLocation(URI.create("/api/v1/reservations/" + result.reservation().id()));

        // A5.2 — Idempotence: 201 for creation, 200 for replay
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return new ResponseEntity<>(result.reservation(), headers, status);
    }

    // ── A5.1-2 — Read a reservation at the announced URI ────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> findById(
            @PathVariable String id,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret) {

        ResponseEntity<?> authFail = checkAuth(agencySecret);
        if (authFail != null) return authFail;

        Optional<ReservationResponseDTO> response = reservationService.findById(id);
        if (response.isEmpty()) {
            throw new ReservationNotFoundException(id);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", response.get().etag());
        return new ResponseEntity<>(response.get(), headers, HttpStatus.OK);
    }

    // ── A5.3 — Modify under ETag condition (optimistic locking) ─────────
    // Requires If-Match header; stale ETag → 412 Precondition Failed
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> modifier(
            @PathVariable String id,
            @Valid @RequestBody ReservationRequestDTO request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret,
            HttpServletRequest httpRequest) {

        ResponseEntity<?> authFail = checkAuth(agencySecret);
        if (authFail != null) return authFail;

        validateContentType(httpRequest);

        // 428 Precondition Required if If-Match is absent
        if (ifMatch == null || ifMatch.isBlank()) {
            return ResponseEntity.status(428).build(); // PRECONDITION_REQUIRED
        }

        Optional<ReservationResponseDTO> response = reservationService.modifier(id, request, ifMatch);
        if (response.isEmpty()) {
            throw new ReservationNotFoundException(id);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", response.get().etag());
        return ResponseEntity.ok()
                .headers(headers)
                .body(response.get());
    }

    // ── A5.3-4 — Cancel (idempotent DELETE) ─────────────────────────────
    // First call → 204 No Content; subsequent calls on same cancelled
    // reservation → still 204 (idempotent effect, same response code).
    // Unknown reservation → 404.
    @DeleteMapping("/{id}")
    public ResponseEntity<?> annuler(
            @PathVariable String id,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret) {

        ResponseEntity<?> authFail = checkAuth(agencySecret);
        if (authFail != null) return authFail;

        boolean cancelled = reservationService.annuler(id);
        if (!cancelled) {
            throw new ReservationNotFoundException(id);
        }
        return ResponseEntity.noContent().build(); // 204
    }
}