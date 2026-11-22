package hai704.tp.demo.client;

import hai704.tp.demo.dto.LinkDTO;
import hai704.tp.demo.dto.ReservationRequestDTO;
import hai704.tp.demo.dto.ReservationResponseDTO;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

public class ReservationClient {

    private final RestClient restClient;
    private final String agencySecret;

    public ReservationClient(RestClient restClient, String agencySecret) {
        this.restClient = restClient;
        this.agencySecret = agencySecret;
    }

    // POST /api/v1/reservations
    public ReservationResponseDTO creer(ReservationRequestDTO request, String idempotencyKey) {
        var requestSpec = restClient.post()
                .uri("/api/v1/reservations")
                .header("X-Agency-Secret", agencySecret);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            requestSpec.header("Idempotency-Key", idempotencyKey);
        }
        return requestSpec
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                .body(ReservationResponseDTO.class);
    }

    // GET /api/v1/reservations/{id}
    public Optional<ReservationResponseDTO> getReservation(String id) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/v1/reservations/{id}", id)
                    .header("X-Agency-Secret", agencySecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .body(ReservationResponseDTO.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw e;
        }
    }

    // PUT /api/v1/reservations/{id} (avec ETag pour concurrence optimiste)
    public Optional<ReservationResponseDTO> modifier(String id, ReservationRequestDTO request, String ifMatch) {
        try {
            return Optional.ofNullable(restClient.put()
                    .uri("/api/v1/reservations/{id}", id)
                    .header("X-Agency-Secret", agencySecret)
                    .header("If-Match", ifMatch)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .body(ReservationResponseDTO.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            if (e.getStatusCode().value() == 412) throw new PreconditionFailedException("ETag périmé");
            throw e;
        }
    }

    // DELETE /api/v1/reservations/{id}
    public boolean annuler(String id) {
        try {
            restClient.delete()
                    .uri("/api/v1/reservations/{id}", id)
                    .header("X-Agency-Secret", agencySecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                    .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return false;
            throw e;
        }
    }

    // Navigation HATEOAS depuis une réservation
    public Optional<ReservationResponseDTO> followLink(ReservationResponseDTO reservation, String rel) {
        LinkDTO link = reservation._links().get(rel);
        if (link == null) return Optional.empty();

        try {
            if ("self".equals(rel)) {
                return getReservation(extractIdFromHref(link.href()));
            } else if ("cancel".equals(rel)) {
                String reservationId = extractIdFromHref(link.href());
                annuler(reservationId);
                return getReservation(reservationId);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String extractIdFromHref(String href) {
        return href.substring(href.lastIndexOf('/') + 1);
    }

    private void handleClientError(HttpRequest request, ClientHttpResponse response) {
        try {
            throw new ReservationClientException("Erreur client: " + response.getStatusCode(),
                    response.getStatusCode().value(), response.getStatusText());
        } catch (IOException e) {
            throw new ReservationClientException("Erreur client: " + e.getMessage(), 0, e.getMessage());
        }
    }

    private void handleServerError(HttpRequest request, ClientHttpResponse response) {
        try {
            throw new ReservationClientException("Erreur serveur: " + response.getStatusCode(),
                    response.getStatusCode().value(), response.getStatusText());
        } catch (IOException e) {
            throw new ReservationClientException("Erreur serveur: " + e.getMessage(), 0, e.getMessage());
        }
    }

    // Exceptions personnalisées
    public static class ReservationClientException extends RuntimeException {
        private final int statusCode;
        private final String statusText;

        public ReservationClientException(String message, int statusCode, String statusText) {
            super(message);
            this.statusCode = statusCode;
            this.statusText = statusText;
        }

        public int getStatusCode() { return statusCode; }
        public String getStatusText() { return statusText; }
    }

    public static class PreconditionFailedException extends RuntimeException {
        public PreconditionFailedException(String message) { super(message); }
    }
}