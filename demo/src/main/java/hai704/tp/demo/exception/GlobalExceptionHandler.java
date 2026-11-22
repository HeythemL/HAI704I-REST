package hai704.tp.demo.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
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

    // ==================== 412 Precondition Failed - ETag périmé (concurrence optimiste) ====================
    @ExceptionHandler(OptimisticLockingException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, ex.getMessage());
        problem.setType(URI.create("https://api.hotel.example.com/errors/precondition-failed"));
        problem.setTitle("Precondition Failed");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 415 Unsupported Media Type ====================
    @ExceptionHandler({InvalidMediaTypeException.class, HttpMediaTypeNotSupportedException.class})
    public ProblemDetail handleInvalidMediaType(Exception ex) {
        String expected = "application/json";
        String received = "unknown";
        if (ex instanceof HttpMediaTypeNotSupportedException mediaEx) {
            received = mediaEx.getContentType() != null ? mediaEx.getContentType().toString() : "unknown";
            expected = mediaEx.getSupportedMediaTypes().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElse("application/json");
        } else if (ex instanceof InvalidMediaTypeException customEx) {
            expected = customEx.getExpectedMediaType();
            received = customEx.getReceivedMediaType();
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                String.format("Type de média non supporté. Attendu: %s, Reçu: %s", expected, received));
        problem.setType(URI.create("https://api.hotel.example.com/errors/unsupported-media-type"));
        problem.setTitle("Unsupported Media Type");
        problem.setProperty("expected", expected);
        problem.setProperty("received", received);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 405 Method Not Allowed ====================
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
                ex.getMessage());
        problem.setType(URI.create("https://api.hotel.example.com/errors/method-not-allowed"));
        problem.setTitle("Method Not Allowed");
        problem.setProperty("supportedMethods", ex.getSupportedMethods());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 400 Bad Request - Validation métier (ex: dates invalides) ====================
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://api.hotel.example.com/errors/bad-request"));
        problem.setTitle("Bad Request");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 422 Unprocessable Entity - Validation @Validated sur paramètres ====================
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Validation des paramètres échouée");
        problem.setType(URI.create("https://api.hotel.example.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");

        Map<String, String> paramErrors = new HashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            result.getResolvableErrors().forEach(error ->
                    paramErrors.put(result.getMethodParameter().getParameterName(), error.getDefaultMessage())
            );
        });
        problem.setProperty("parameterErrors", paramErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 409 Conflict - Violation contrainte unique (ex: idempotency key) ====================
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = "Conflit de données : " + ex.getMostSpecificCause().getMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
        problem.setType(URI.create("https://api.hotel.example.com/errors/conflict"));
        problem.setTitle("Conflict");
        problem.setProperty("cause", ex.getMostSpecificCause().getClass().getSimpleName());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 500 - Erreur transaction (ex: contrainte @Version) ====================
    @ExceptionHandler(TransactionSystemException.class)
    public ProblemDetail handleTransactionSystem(TransactionSystemException ex) {
        Throwable cause = ex.getRootCause();
        String message = cause != null ? cause.getMessage() : "Erreur de transaction";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, message);
        problem.setType(URI.create("https://api.hotel.example.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("cause", cause != null ? cause.getClass().getSimpleName() : "Unknown");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ==================== 500 Internal Server Error - Catch-all ====================
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur interne du serveur");
        problem.setType(URI.create("https://api.hotel.example.com/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setProperty("exception", ex.getClass().getSimpleName());
        problem.setProperty("message", ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}