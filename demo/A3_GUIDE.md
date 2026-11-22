# A3 — Sémantique HTTP des Erreurs : Guide Complet d'Implémentation

> **Objectif** : Centraliser la gestion d'erreurs avec `ProblemDetail` (RFC 9457), valider les entrées, et garantir que chaque code d'erreur requis (400, 404, 405, 409, 415, 422) est provoqué, documenté et justifié par rapport à ses voisins.

---

## 📁 Structure des Fichiers (Projet `hotel`)

```
hotel/
├── src/
│   └── main/
│       ├── java/
│       │   └── hai704/tp/hotel/
│       │       ├── HotelApplication.java
│       │       ├── config/
│       │       │   └── ProblemDetailConfig.java          ← NOUVEAU : Configuration ProblemDetail
│       │       ├── controller/
│       │       │   ├── OffreController.java              ← EXISTANT : Ajouter @Valid
│       │       │   └── PhotoController.java              ← EXISTANT : Ajouter @Valid
│       │       ├── dto/
│       │       │   ├── OffreRequestDTO.java              ← NOUVEAU : DTO validation recherche
│       │       │   ├── OffreResponseDTO.java             ← EXISTANT
│       │       │   └── ReservationRequestDTO.java        ← NOUVEAU : DTO validation réservation
│       │       ├── exception/
│       │       │   ├── GlobalExceptionHandler.java       ← NOUVEAU : @RestControllerAdvice centralisé
│       │       │   ├── OffreNotFoundException.java       ← NOUVEAU : 404 métier
│       │       │   ├── ReservationConflictException.java ← NOUVEAU : 409 conflit chambre
│       │       │   └── InvalidMediaTypeException.java    ← NOUVEAU : 415 type média
│       │       ├── model/
│       │       │   ├── Offre.java
│       │       │   └── Reservation.java
│       │       ├── repository/
│       │       │   ├── OffreRepository.java
│       │       │   └── ReservationRepository.java
│       │       └── service/
│       │           ├── OffreService.java
│       │           └── ReservationService.java
│       └── resources/
│           └── application.yml
```

---

## 1. Configuration ProblemDetail (RFC 9457)

### Fichier : `config/ProblemDetailConfig.java`

```java
package hai704.tp.hotel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProblemDetailConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        // Force ProblemDetail (application/problem+json) comme format d'erreur par défaut
        configurer.defaultContentType(MediaType.APPLICATION_PROBLEM_JSON)
                  .mediaType("problem", MediaType.APPLICATION_PROBLEM_JSON);
    }
}
```

**Pourquoi ?** Spring Boot 3+ utilise `ProblemDetail` nativement. Cette configuration garantit que les erreurs retournent `application/problem+json` au lieu de `application/json` générique.

---

## 2. Gestionnaire Centralisé d'Exceptions

### Fichier : `exception/GlobalExceptionHandler.java`

```java
package hai704.tp.hotel.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ==================== 400 Bad Request - Corps illisible / JSON malformé ====================
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ProblemDetail handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Corps de requête illisible ou JSON malformé");
        problem.setType(URI.create("https://api.hotel.example.com/errors/bad-request"));
        problem.setTitle("Bad Request");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("cause", ex.getMostSpecificCause() != null
            ? ex.getMostSpecificCause().getClass().getSimpleName()
            : "Unknown");
        return problem;
    }

    // ==================== 422 Unprocessable Entity - Validation @Valid échouée ====================
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            org.springframework.http.HttpHeaders headers,
            HttpStatusCode status,
            org.springframework.web.context.request.WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Données de requête syntaxiquement valides mais sémantiquement invalides");
        problem.setType(URI.create("https://api.hotel.example.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    // Handler pour @Valid sur paramètres de requête (HandlerMethodValidationException - Spring 6+)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleValidationException(HandlerMethodValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
            "Paramètres de requête invalides");
        problem.setType(URI.create("https://api.hotel.example.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");

        Map<String, String> paramErrors = new HashMap<>();
        // Spring 6: utiliser getParameterValidationResults() ou getAllErrors()
        ex.getParameterValidationResults().forEach(result -> {
            result.getResolvableErrors().forEach(error -> {
                String[] codes = error.getCodes();
                String field = codes.length > 0 ? codes[0] : "unknown";
                paramErrors.put(field, error.getDefaultMessage());
            });
        });
        problem.setProperty("paramErrors", paramErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 404 Not Found - Ressource inconnue ====================
    @ExceptionHandler(OffreNotFoundException.class)
    public ProblemDetail handleOffreNotFound(OffreNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://api.hotel.example.com/errors/not-found"));
        problem.setTitle("Not Found");
        problem.setProperty("resourceType", "Offre");
        problem.setProperty("resourceId", ex.getResourceId());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail handleReservationNotFound(ReservationNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://api.hotel.example.com/errors/not-found"));
        problem.setTitle("Not Found");
        problem.setProperty("resourceType", "Reservation");
        problem.setProperty("resourceId", ex.getResourceId());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 409 Conflict - Conflit métier (chambre déjà réservée) ====================
    @ExceptionHandler(ReservationConflictException.class)
    public ProblemDetail handleReservationConflict(ReservationConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://api.hotel.example.com/errors/conflict"));
        problem.setTitle("Conflict");
        problem.setProperty("offreId", ex.getOffreId());
        problem.setProperty("dateDebut", ex.getDateDebut());
        problem.setProperty("dateFin", ex.getDateFin());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 415 Unsupported Media Type ====================
    @ExceptionHandler(InvalidMediaTypeException.class)
    public ProblemDetail handleInvalidMediaType(InvalidMediaTypeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        problem.setType(URI.create("https://api.hotel.example.com/errors/unsupported-media-type"));
        problem.setTitle("Unsupported Media Type");
        problem.setProperty("expected", ex.getExpectedMediaType());
        problem.setProperty("received", ex.getReceivedMediaType());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 405 Method Not Allowed - Géré automatiquement par Spring ====================
    // Spring ajoute automatiquement le header 'Allow' quand @RequestMapping(method=...) ne matche pas
    // Pas besoin de handler explicite, MAIS vérifier que les contrôleurs déclarent bien les méthodes supportées

    // ==================== 500 Internal Server Error - Fallback ====================
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Erreur interne du serveur");
        problem.setType(URI.create("https://api.hotel.example.com/errors/internal"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        // Ne JAMAIS exposer la stack trace ou détails internes en production
        return problem;
    }
}
```

---

## 3. Exceptions Métier Personnalisées

### Fichier : `exception/OffreNotFoundException.java`

```java
package hai704.tp.hotel.exception;

public class OffreNotFoundException extends RuntimeException {
    private final String resourceId;

    public OffreNotFoundException(String resourceId) {
        super("Offre introuvable : " + resourceId);
        this.resourceId = resourceId;
    }

    public String getResourceId() { return resourceId; }
}
```

### Fichier : `exception/ReservationNotFoundException.java`

```java
package hai704.tp.hotel.exception;

public class ReservationNotFoundException extends RuntimeException {
    private final String resourceId;

    public ReservationNotFoundException(String resourceId) {
        super("Réservation introuvable : " + resourceId);
        this.resourceId = resourceId;
    }

    public String getResourceId() { return resourceId; }
}
```

### Fichier : `exception/ReservationConflictException.java`

```java
package hai704.tp.hotel.exception;

import java.time.LocalDate;

public class ReservationConflictException extends RuntimeException {
    private final String offreId;
    private final LocalDate dateDebut;
    private final LocalDate dateFin;

    public ReservationConflictException(String offreId, LocalDate dateDebut, LocalDate dateFin) {
        super(String.format("Chambre déjà réservée pour l'offre %s du %s au %s", offreId, dateDebut, dateFin));
        this.offreId = offreId;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    public String getOffreId() { return offreId; }
    public LocalDate getDateDebut() { return dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
}
```

### Fichier : `exception/InvalidMediaTypeException.java`

```java
package hai704.tp.hotel.exception;

public class InvalidMediaTypeException extends RuntimeException {
    private final String expectedMediaType;
    private final String receivedMediaType;

    public InvalidMediaTypeException(String expected, String received) {
        super(String.format("Type de média non supporté. Attendu: %s, Reçu: %s", expected, received));
        this.expectedMediaType = expected;
        this.receivedMediaType = received;
    }

    public String getExpectedMediaType() { return expectedMediaType; }
    public String getReceivedMediaType() { return receivedMediaType; }
}
```

---

## 4. DTOs avec Validation Bean Validation (JSR-380)

### Fichier : `dto/OffreRequestDTO.java` — Pour `GET /api/v1/offres?...`

```java
package hai704.tp.hotel.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record OffreRequestDTO(
    @NotBlank(message = "La ville est obligatoire")
    @Size(min = 2, max = 100, message = "La ville doit contenir entre 2 et 100 caractères")
    String ville,

    @NotNull(message = "La date de début est obligatoire")
    LocalDate dateDebut,

    @NotNull(message = "La date de fin est obligatoire")
    LocalDate dateFin,

    @NotNull(message = "Le nombre de personnes est obligatoire")
    @Min(value = 1, message = "Au moins 1 personne")
    @Max(value = 10, message = "Maximum 10 personnes")
    Integer nbPersonnes
) {
    // Validation croisée : dateDebut <= dateFin
    public boolean isDateRangeValid() {
        return dateDebut != null && dateFin != null && !dateDebut.isAfter(dateFin);
    }
}
```

### Fichier : `dto/ReservationRequestDTO.java` — Pour `POST /api/v1/reservations`

```java
package hai704.tp.hotel.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ReservationRequestDTO(
    @NotBlank(message = "L'identifiant de l'offre est obligatoire")
    String offreId,

    @NotBlank(message = "Le nom du client est obligatoire")
    @Size(min = 2, max = 200, message = "Le nom doit contenir entre 2 et 200 caractères")
    String nomClient,

    @NotNull(message = "La date de début est obligatoire")
    LocalDate dateDebut,

    @NotNull(message = "La date de fin est obligatoire")
    LocalDate dateFin
) {
    public boolean isDateRangeValid() {
        return dateDebut != null && dateFin != null && !dateDebut.isAfter(dateFin);
    }
}
```

---

## 5. Contrôleurs Mis à Jour

### Fichier : `controller/OffreController.java`

```java
package hai704.tp.hotel.controller;

import hai704.tp.hotel.dto.OffreRequestDTO;
import hai704.tp.hotel.dto.OffreResponseDTO;
import hai704.tp.hotel.exception.InvalidMediaTypeException;
import hai704.tp.hotel.exception.OffreNotFoundException;
import hai704.tp.hotel.service.OffreService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        // Validation croisée manuelle (dateDebut <= dateFin)
        if (!criteria.isDateRangeValid()) {
            throw new hai704.tp.hotel.exception.ValidationException(
                "La date de début doit être antérieure ou égale à la date de fin");
        }
        return ResponseEntity.ok(offreService.rechercher(criteria));
    }

    // GET /api/v1/offres/{id}
    @GetMapping("/{id}")
    public ResponseEntity<OffreResponseDTO> getOffre(@PathVariable String id) {
        return offreService.findById(id)
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
```

### Fichier : `controller/ReservationController.java` (NOUVEAU pour A3/A5)

```java
package hai704.tp.hotel.controller;

import hai704.tp.hotel.dto.ReservationRequestDTO;
import hai704.tp.hotel.dto.ReservationResponseDTO;
import hai704.tp.hotel.exception.OffreNotFoundException;
import hai704.tp.hotel.exception.ReservationConflictException;
import hai704.tp.hotel.exception.ReservationNotFoundException;
import hai704.tp.hotel.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservationResponseDTO> creerReservation(
            @Valid @RequestBody ReservationRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Agency-Secret", required = false) String agencySecret) {

        // Auth basique (A5) - 401 si secret invalide/absent
        if (!reservationService.isValidAgencySecret(agencySecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "X-Agency-Secret realm=\"Hotel API\"")
                    .build();
        }

        // Vérification type média - 415 si pas application/json
        // Spring le gère via consumes=, mais on peut forcer la vérification

        var reservation = reservationService.creer(request, idempotencyKey);

        // Idempotence : 201 pour création, 200 pour rejeu même clé
        HttpStatus status = reservation.isNew() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status)
                .location(URI.create("/api/v1/reservations/" + reservation.getId()))
                .body(reservation);
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
                .map(ResponseEntity::ok)
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
            // 428 Precondition Required - header If-Match manquant
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).build();
        }

        return reservationService.modifier(id, request, ifMatch)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        // Si ETag ne match pas -> service lève exception -> 412 via GlobalExceptionHandler
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
```

---

## 6. Services (Logique Métier + Lancement Exceptions)

### Extraits clés dans `service/OffreService.java`

```java
public OffreResponseDTO rechercher(OffreRequestDTO criteria) {
    // Validation croisée déjà faite dans contrôleur
    List<Offre> offres = offreRepository.findByVilleAndDates(
            criteria.ville(), criteria.dateDebut(), criteria.dateFin(), criteria.nbPersonnes());

    // Construction enveloppe collection (A2)
    return new OffreResponseDTO(
            offres.stream().map(this::toDTO).toList(),
            buildLinks(criteria),
            offres.size()
    );
}

public Optional<byte[]> getPhoto(String id) {
    return offreRepository.findById(id)
            .map(Offre::getPhoto)
            .filter(photo -> photo != null && photo.length > 0);
}
```

### Extraits clés dans `service/ReservationService.java`

```java
public ReservationResult creer(ReservationRequestDTO request, String idempotencyKey) {
    // 1. Vérifier idempotence (clé en BDD ou cache)
    if (idempotencyKey != null) {
        Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new ReservationResult(existing.get(), false); // Rejeu -> 200 OK
        }
    }

    // 2. Vérifier offre existe
    Offre offre = offreRepository.findById(request.offreId())
            .orElseThrow(() -> new OffreNotFoundException(request.offreId()));

    // 3. Vérifier disponibilité (pas de chevauchement dates) -> 409 Conflict
    boolean conflict = reservationRepository.existsOverlapping(
            request.offreId(), request.dateDebut(), request.dateFin());
    if (conflict) {
        throw new ReservationConflictException(request.offreId(), request.dateDebut(), request.dateFin());
    }

    // 4. Créer réservation
    Reservation reservation = new Reservation(...);
    reservation.setIdempotencyKey(idempotencyKey);
    reservationRepository.save(reservation);

    return new ReservationResult(reservation, true); // Nouvelle -> 201 Created
}

public Optional<ReservationResponseDTO> modifier(String id, ReservationRequestDTO request, String ifMatch) {
    Reservation reservation = reservationRepository.findById(id)
            .orElseThrow(() -> new ReservationNotFoundException(id));

    // Concurrence optimiste : comparer ETag
    String currentETag = reservation.computeETag();
    if (!currentETag.equals(ifMatch.replace("\"", ""))) {
        throw new OptimisticLockingException("ETag périmé : modification concurrente détectée");
    }

    // Appliquer modifications...
    reservationRepository.save(reservation);
    return Optional.of(toDTO(reservation));
}
```

---

## 7. Tests curl pour Chaque Code d'Erreur (Relevés Obligatoires)

### 400 Bad Request — JSON malformé
```bash
curl -i -X POST http://localhost:8081/api/v1/reservations \
  -H "Content-Type: application/json" \
  -H "X-Agency-Secret: secret123" \
  -d '{ "offreId": "H1-1", "nomClient": "Dupont" }'  # JSON incomplet (manque dates)
```
**Attendu** : `400 Bad Request`, `Content-Type: application/problem+json`, `type: ".../bad-request"`

### 422 Unprocessable Entity — Validation échouée (données syntaxiquement OK mais invalides)
```bash
curl -i -X POST http://localhost:8081/api/v1/reservations \
  -H "Content-Type: application/json" \
  -H "X-Agency-Secret: secret123" \
  -d '{
    "offreId": "",
    "nomClient": "D",
    "dateDebut": "2026-12-01",
    "dateFin": "2026-11-30"
  }'
```
**Attendu** : `422 Unprocessable Entity`, `fieldErrors` avec `offreId`, `nomClient`, `dateFin`

### 404 Not Found — Offre inexistante
```bash
curl -i http://localhost:8081/api/v1/offres/OFFRE-INEXISTANTE
```
**Attendu** : `404 Not Found`, `type: ".../not-found"`, `resourceType: "Offre"`, `resourceId: "OFFRE-INEXISTANTE"`

### 405 Method Not Allowed — Méthode non supportée
```bash
curl -i -X POST http://localhost:8081/api/v1/offres/H1-1
```
**Attendu** : `405 Method Not Allowed`, header `Allow: GET` (car seul GET défini sur cette URI)

### 409 Conflict — Chambre déjà réservée
```bash
# 1ère réservation (succès 201)
curl -i -X POST http://localhost:8081/api/v1/reservations \
  -H "Content-Type: application/json" \
  -H "X-Agency-Secret: secret123" \
  -H "Idempotency-Key: key-123" \
  -d '{"offreId":"H1-1","nomClient":"Dupont","dateDebut":"2026-12-01","dateFin":"2026-12-05"}'

# 2ème réservation même offre/dates (échec 409)
curl -i -X POST http://localhost:8081/api/v1/reservations \
  -H "Content-Type: application/json" \
  -H "X-Agency-Secret: secret123" \
  -H "Idempotency-Key: key-456" \
  -d '{"offreId":"H1-1","nomClient":"Martin","dateDebut":"2026-12-01","dateFin":"2026-12-05"}'
```
**Attendu** : `409 Conflict`, `type: ".../conflict"`, `offreId`, `dateDebut`, `dateFin`

### 415 Unsupported Media Type — Mauvais Content-Type
```bash
curl -i -X POST http://localhost:8081/api/v1/reservations \
  -H "Content-Type: text/plain" \
  -H "X-Agency-Secret: secret123" \
  -d '{"offreId":"H1-1","nomClient":"Dupont","dateDebut":"2026-12-01","dateFin":"2026-12-05"}'
```
**Attendu** : `415 Unsupported Media Type`, `expected: "application/json"`, `received: "text/plain"`

---

## 8. Justifications Croisées (Obligatoires dans Compte-Rendu)

| Code | Situation Réelle | Pourquoi PAS le voisin ? |
|------|------------------|---------------------------|
| **400** | Corps illisible / JSON malformé (parse error) | ≠ 422 : ici le client n'a pas envoyé du JSON valide |
| **422** | JSON valide mais données invalides (champs vides, dates incohérentes) | ≠ 400 : le parsing a réussi, c'est la sémantique qui échoue |
| **404** | Ressource inconnue (offre ID inexistant, réservation purgée) | ≠ 409 : la ressource n'existe pas vs existe mais en conflit |
| **409** | Conflit métier : chambre déjà réservée pour ces dates | ≠ 404 : l'offre existe, la réservation existe, mais impossible d'en créer une 2e |
| **415** | Content-Type non supporté (ex: text/plain sur endpoint JSON) | ≠ 400/422 : erreur sur le type média, pas sur le contenu |
| **405** | Méthode HTTP non définie sur cette URI | ≠ 404 : l'URI existe, mais pas cette méthode (header `Allow` présent) |

---

## 9. Checklist de Validation A3

- [ ] `@RestControllerAdvice` + `ProblemDetail` centralise **toutes** les erreurs
- [ ] Format `application/problem+json` pour **chaque** erreur (type, title, status, detail, timestamp)
- [ ] **400** : JSON malformé → `HttpMessageNotReadableException`
- [ ] **422** : `@Valid` échoué → `MethodArgumentNotValidException` + `HandlerMethodValidationException`
- [ ] **404** : `OffreNotFoundException`, `ReservationNotFoundException` levées par service
- [ ] **409** : `ReservationConflictException` levée si chambre déjà réservée
- [ ] **415** : `InvalidMediaTypeException` ou Spring natif (`consumes=`)
- [ ] **405** : Header `Allow` présent automatiquement (vérifier `@RequestMapping(method=...)`)
- [ ] Chaque code testé avec `curl -i` et relevé dans `releves/`
- [ ] Justifications croisées écrites dans Compte-Rendu (400≠422, 404≠409, 401≠403)

---

## 10. Commandes Utiles

```bash
# Build & run hotel
cd hotel
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

# Test rapide tous codes
curl -i http://localhost:8081/api/v1/offres/INEXISTANT           # 404
curl -i -X POST http://localhost:8081/api/v1/offres/H1-1         # 405
curl -i -X POST http://localhost:8081/api/v1/reservations \
  -H "Content-Type: application/json" -d 'invalid json'          # 400
curl -i -X POST http://localhost:8081/api/v1/reservations \
  -H "Content-Type: text/plain" -d '{}'                          # 415
```

---

*Ce guide couvre l'implémentation complète de l'étape A3 du TP3 HAI704I 2026-2027.*