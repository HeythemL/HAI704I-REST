package hai704.tp.hotel.service;

import hai704.tp.hotel.dto.ReservationRequestDTO;
import hai704.tp.hotel.dto.ReservationResponseDTO;
import hai704.tp.hotel.dto.LinkDTO;
import hai704.tp.hotel.exception.OffreNotFoundException;
import hai704.tp.hotel.exception.OptimisticLockingException;
import hai704.tp.hotel.exception.ReservationConflictException;
import hai704.tp.hotel.exception.ReservationNotFoundException;
import hai704.tp.hotel.model.Offre;
import hai704.tp.hotel.model.Reservation;
import hai704.tp.hotel.repository.OffreRepository;
import hai704.tp.hotel.repository.ReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final OffreRepository offreRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // A5.1 — Agency secret externalized via configuration
    @org.springframework.beans.factory.annotation.Value("${hotel.agency.secret:secret123}")
    private String agencySecret;

    public ReservationService(ReservationRepository reservationRepository, OffreRepository offreRepository) {
        this.reservationRepository = reservationRepository;
        this.offreRepository = offreRepository;
    }

    public boolean isValidAgencySecret(String secret) {
        return agencySecret != null && agencySecret.equals(secret);
    }

    public record ReservationResult(ReservationResponseDTO reservation, boolean isNew) {}

    @Transactional
    public ReservationResult creer(ReservationRequestDTO request, String idempotencyKey) {
        if (!request.isDateRangeValid()) {
            throw new IllegalArgumentException("La date de début doit être antérieure ou égale à la date de fin");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return new ReservationResult(toDTO(existing.get(), false), false);
            }
        }

        Offre offre = offreRepository.findById(request.offreId())
                .orElseThrow(() -> new OffreNotFoundException(request.offreId()));

        boolean conflict = reservationRepository.existsOverlapping(
                request.offreId(), request.dateDebut(), request.dateFin());
        if (conflict) {
            throw new ReservationConflictException(request.offreId(), request.dateDebut(), request.dateFin());
        }

        String reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Reservation reservation = new Reservation(
                reservationId,
                request.offreId(),
                request.nomClient(),
                request.dateDebut(),
                request.dateFin(),
                offre.getPrixTotal(),
                idempotencyKey
        );
        reservationRepository.save(reservation);
        entityManager.flush();

        return new ReservationResult(toDTO(reservation, true), true);
    }

    @Transactional(readOnly = true)
    public Optional<ReservationResponseDTO> findById(String id) {
        return reservationRepository.findById(id)
                .map(r -> toDTO(r, false));
    }

    @Transactional
    public Optional<ReservationResponseDTO> modifier(String id, ReservationRequestDTO request, String ifMatch) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        String currentETag = reservation.computeETag().replace("\"", "").trim();
        String providedETag = ifMatch != null ? ifMatch.replace("\"", "").trim() : "";
        if (!currentETag.equals(providedETag)) {
            throw new OptimisticLockingException("ETag périmé : modification concurrente détectée");
        }

        if (!request.isDateRangeValid()) {
            throw new IllegalArgumentException("La date de début doit être antérieure ou égale à la date de fin");
        }

        boolean conflict = reservationRepository.existsOverlappingExcludingId(
                request.offreId(), request.dateDebut(), request.dateFin(), id);
        if (conflict) {
            throw new ReservationConflictException(request.offreId(), request.dateDebut(), request.dateFin());
        }

        Offre offre = offreRepository.findById(request.offreId())
                .orElseThrow(() -> new OffreNotFoundException(request.offreId()));

        reservation.setOffreId(request.offreId());
        reservation.setNomClient(request.nomClient());
        reservation.setDateDebut(request.dateDebut());
        reservation.setDateFin(request.dateFin());
        reservation.setPrixPaye(offre.getPrixTotal());

        reservationRepository.save(reservation);
        entityManager.flush();

        return Optional.of(toDTO(reservation, false));
    }

    @Transactional
    public boolean annuler(String id) {
        Optional<Reservation> opt = reservationRepository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        Reservation reservation = opt.get();
        if ("CANCELLED".equals(reservation.getStatut())) {
            return true;
        }
        reservation.setStatut("CANCELLED");
        reservationRepository.save(reservation);
        entityManager.flush();
        return true;
    }

    ReservationResponseDTO toDTO(Reservation reservation, boolean isNew) {
        Map<String, LinkDTO> links = new HashMap<>();
        links.put("self", LinkDTO.self("/api/v1/reservations/" + reservation.getId()));
        links.put("offre", LinkDTO.offre("/api/v1/offres/" + reservation.getOffreId()));
        links.put("update", LinkDTO.update("/api/v1/reservations/" + reservation.getId()));
        links.put("cancel", LinkDTO.cancel("/api/v1/reservations/" + reservation.getId()));

        return new ReservationResponseDTO(
                reservation.getId(),
                reservation.getOffreId(),
                reservation.getNomClient(),
                reservation.getDateDebut().toString(),
                reservation.getDateFin().toString(),
                reservation.getStatut(),
                reservation.getPrixPaye(),
                links,
                isNew,
                reservation.computeETag()
        );
    }
}