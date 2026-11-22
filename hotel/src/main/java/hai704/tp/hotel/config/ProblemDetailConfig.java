package hai704.tp.hotel.config;

import hai704.tp.hotel.exception.InvalidMediaTypeException;
import hai704.tp.hotel.exception.OffreNotFoundException;
import hai704.tp.hotel.exception.OptimisticLockingException;
import hai704.tp.hotel.exception.ReservationConflictException;
import hai704.tp.hotel.exception.ReservationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ProblemDetailConfig {

    // ══════════════ 400 Bad Request — Malformed JSON body ══════════════
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corps de requête illisible ou JSON malformé");
        pd.setTitle("Bad Request");
        pd.setType(URI.create("https://example.com/probs/bad-request"));
        pd.setProperty("cause", ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getClass().getSimpleName() : "Unknown");
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    // ══════════════ 422 Unprocessable Entity — @Valid failures ══════════════
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Données de requête syntaxiquement valides mais sémantiquement invalides");
        pd.setTitle("Validation Failed");
        pd.setType(URI.create("https://example.com/probs/validation-failed"));

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        pd.setProperty("fieldErrors", fieldErrors);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    // ══════════════ 422 — @Validated on query parameters (Spring 6+) ══════════════
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Paramètres de requête invalides");
        pd.setTitle("Validation Failed");
        pd.setType(URI.create("https://example.com/probs/validation-failed"));

        Map<String, String> paramErrors = new HashMap<>();
        ex.getParameterValidationResults().forEach(result ->
                result.getResolvableErrors().forEach(error ->
                        paramErrors.put(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())
                )
        );
        pd.setProperty("parameterErrors", paramErrors);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    // ══════════════ 404 Not Found ══════════════
    @ExceptionHandler(OffreNotFoundException.class)
    public ProblemDetail handleOffreNotFound(OffreNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Offre non trouvée");
        pd.setType(URI.create("https://example.com/probs/offre-not-found"));
        pd.setProperty("offreId", ex.getResourceId());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail handleReservationNotFound(ReservationNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Réservation non trouvée");
        pd.setType(URI.create("https://example.com/probs/reservation-not-found"));
        pd.setProperty("reservationId", ex.getResourceId());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    // ══════════════ 409 Conflict — Room already booked ══════════════
    @ExceptionHandler(ReservationConflictException.class)
    public ProblemDetail handleReservationConflict(ReservationConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflit de réservation");
        pd.setType(URI.create("https://example.com/probs/reservation-conflict"));
        pd.setProperty("offreId", ex.getOffreId());
        pd.setProperty("dateDebut", ex.getDateDebut().toString());
        pd.setProperty("dateFin", ex.getDateFin().toString());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    // ══════════════ 412 Precondition Failed — Stale ETag ══════════════
    @ExceptionHandler(OptimisticLockingException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, ex.getMessage());
        pd.setTitle("Conflit de version (optimistic locking)");
        pd.setType(URI.create("https://example.com/probs/optimistic-locking"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    // ══════════════ 415 Unsupported Media Type ══════════════
    @ExceptionHandler(InvalidMediaTypeException.class)
    public ProblemDetail handleInvalidMediaType(InvalidMediaTypeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        pd.setTitle("Type de média non supporté");
        pd.setType(URI.create("https://example.com/probs/invalid-media-type"));
        pd.setProperty("expected", ex.getExpectedMediaType());
        pd.setProperty("received", ex.getReceivedMediaType());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    // ══════════════ 400 — Business validation (dates, etc.) ══════════════
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Argument invalide");
        pd.setType(URI.create("https://example.com/probs/bad-request"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Paramètre invalide: " + ex.getName() + " doit être de type " + ex.getRequiredType().getSimpleName());
        pd.setTitle("Type de paramètre invalide");
        pd.setType(URI.create("https://example.com/probs/bad-request"));
        pd.setProperty("parameter", ex.getName());
        pd.setProperty("expectedType", ex.getRequiredType().getSimpleName());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    // ══════════════ 500 — Catch-all ══════════════
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur");
        pd.setTitle("Erreur interne");
        pd.setType(URI.create("https://example.com/probs/internal-error"));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}