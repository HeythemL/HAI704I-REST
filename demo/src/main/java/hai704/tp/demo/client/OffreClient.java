package hai704.tp.demo.client;

import hai704.tp.demo.dto.LinkDTO;
import hai704.tp.demo.dto.OffreRequestDTO;
import hai704.tp.demo.dto.OffreResponseDTO;
import hai704.tp.demo.dto.OffreDTO;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

public class OffreClient {

    private final RestClient restClient;

    public OffreClient(RestClient restClient) {
        this.restClient = restClient;
    }

    // GET /api/v1/offres?ville=...&dateDebut=...&dateFin=...&nbPersonnes=...
    public OffreResponseDTO rechercher(OffreRequestDTO criteria) {
        URI uri = buildSearchUri(criteria);
        return restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
                .onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
                .body(OffreResponseDTO.class);
    }

    // GET /api/v1/offres/{id}
    public Optional<OffreDTO> getOffre(String id) {
        try {
            OffreResponseDTO response = restClient.get()
                    .uri("/api/v1/offres/{id}", id)
                    .retrieve()
                    .body(OffreResponseDTO.class);
            return (response != null && !response.offres().isEmpty())
                    ? Optional.of(response.offres().get(0))
                    : Optional.empty();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw e;
        }
    }

    // GET /api/v1/offres/{id}/photo
    public Optional<byte[]> getPhoto(String id) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/v1/offres/{id}/photo", id)
                    .accept(MediaType.IMAGE_JPEG)
                    .retrieve()
                    .body(byte[].class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return Optional.empty();
            throw e;
        }
    }

    // Navigation via liens HATEOAS
    public Optional<OffreDTO> followLink(OffreResponseDTO response, String rel) {
        return response._links().get(rel) != null
                ? followLink(response._links().get(rel))
                : Optional.empty();
    }

    public Optional<OffreDTO> followLink(LinkDTO link) {
        try {
            OffreResponseDTO response = restClient.get()
                    .uri(link.href())
                    .retrieve()
                    .body(OffreResponseDTO.class);
            return (response != null && !response.offres().isEmpty())
                    ? Optional.of(response.offres().get(0))
                    : Optional.empty();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 422) return Optional.empty();
            throw e;
        }
    }

    private URI buildSearchUri(OffreRequestDTO criteria) {
        return org.springframework.web.util.UriComponentsBuilder.fromPath("/api/v1/offres")
                .queryParam("ville", criteria.ville())
                .queryParam("dateDebut", criteria.dateDebut())
                .queryParam("dateFin", criteria.dateFin())
                .queryParam("nbPersonnes", criteria.nbPersonnes())
                .build()
                .toUri();
    }

    private void handleClientError(HttpRequest request, ClientHttpResponse response) {
        try {
            throw new OffreClientException("Erreur client: " + response.getStatusCode(),
                    response.getStatusCode().value(), response.getStatusText());
        } catch (IOException e) {
            throw new OffreClientException("Erreur client: " + e.getMessage(), 0, e.getMessage());
        }
    }

    private void handleServerError(HttpRequest request, ClientHttpResponse response) {
        try {
            throw new OffreClientException("Erreur serveur: " + response.getStatusCode(),
                    response.getStatusCode().value(), response.getStatusText());
        } catch (IOException e) {
            throw new OffreClientException("Erreur serveur: " + e.getMessage(), 0, e.getMessage());
        }
    }

    // Exception personnalisée pour le client
    public static class OffreClientException extends RuntimeException {
        private final int statusCode;
        private final String statusText;

        public OffreClientException(String message, int statusCode, String statusText) {
            super(message);
            this.statusCode = statusCode;
            this.statusText = statusText;
        }

        public int getStatusCode() { return statusCode; }
        public String getStatusText() { return statusText; }
    }
}