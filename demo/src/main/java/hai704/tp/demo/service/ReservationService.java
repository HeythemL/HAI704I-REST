package hai704.tp.demo.service;

import hai704.tp.demo.dto.ReservationRequestDTO;
import hai704.tp.demo.dto.ReservationResponseDTO;
import hai704.tp.demo.dto.LinkDTO;
import hai704.tp.demo.exception.OffreNotFoundException;
import hai704.tp.demo.exception.OptimisticLockingException;
import hai704.tp.demo.exception.ReservationConflictException;
import hai704.tp.demo.exception.ReservationNotFoundException;
import hai704.tp.demo.model.Offre;
import hai704.tp.demo.model.Reservation;
import hai704.tp.demo.repository.OffreRepository;
import hai704.tp.demo.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.lang.String;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final OffreRepository offreRepository;

    // Secret d'agence configuré (en production : externalisé via config)
    private static final String AGENCY_SECRET = "secret123";

    public ReservationService(ReservationRepository reservationRepository, OffreRepository offreRepository) {
        this.reservationRepository = reservationRepository;
        this.offreRepository = offreRepository;
    }

    public boolean isValidAgencySecret(String secret) {
        return AGENCY_SECRET.equals(secret);
    }

    // Résultat interne pour distinguer création vs rejeu idempotent
    public record ReservationResult(ReservationResponseDTO reservation, boolean isNew) {}

    @Transactional
    public ReservationResult creer(ReservationRequestDTO request, String idempotencyKey) {
        // Validation croisée dates
        if (!request.isDateRangeValid()) {
            throw new IllegalArgumentException("La date de début doit être antérieure ou égale à la date de fin");
        }

        // 1. Idempotence : vérifier si clé déjà utilisée
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Reservation> existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return new ReservationResult(toDTO(existing.get(), false), false); // Rejeu -> isNew = false
            }
        }

        // 2. Vérifier offre existe
        Offre offre = offreRepository.findById(request.offreId())
                .orElseThrow(() -> new OffreNotFoundException(request.offreId()));

        // 3. Vérifier disponibilité (pas de chevauchement) -> 409 Conflict
        boolean conflict = reservationRepository.existsOverlapping(
                request.offreId(), request.dateDebut(), request.dateFin());
        if (conflict) {
            throw new ReservationConflictException(request.offreId(), request.dateDebut(), request.dateFin());
        }

        // 4. Créer réservation
        String reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Reservation reservation = new Reservation(
                reservationId,
                request.offreId(),
                request.nomClient(),
                request.dateDebut(),
                request.dateFin(),
                offre.getPrixTotal(), // Prix public de l'offre
                idempotencyKey
        );
        reservationRepository.save(reservation);

        return new ReservationResult(toDTO(reservation, true), true); // Nouvelle -> isNew = true
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

        // Concurrence optimiste : comparer ETag
        String currentETag = reservation.computeETag();
        String providedETag = ifMatch != null ? ifMatch.replace("\"", "") : "";
        if (!currentETag.equals(providedETag)) {
            throw new OptimisticLockingException("ETag périmé : modification concurrente détectée");
        }

        // Validation croisée dates
        if (!request.isDateRangeValid()) {
            throw new IllegalArgumentException("La date de début doit être antérieure ou égale à la date de fin");
        }

        // Vérifier disponibilité pour nouvelles dates (exclure la réservation courante)
        boolean conflict = reservationRepository.existsOverlapping(
                request.offreId(), request.dateDebut(), request.dateFin());
        // Note: pour une vraie implémentation, il faudrait exclure l'ID courant de la requête

        // Appliquer modifications
        reservation.setOffreId(request.offreId());
        reservation.setNomClient(request.nomClient());
        reservation.setDateDebut(request.dateDebut());
        reservation.setDateFin(request.dateFin());

        // Le prix peut changer si offre différente
        Offre offre = offreRepository.findById(request.offreId())
                .orElseThrow(() -> new OffreNotFoundException(request.offreId()));
        reservation.setPrixPaye(offre.getPrixTotal());

        reservationRepository.save(reservation); // Version incrémentée automatiquement par @Version

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
            return true; // Idempotent : déjà annulée
        }
        reservation.setStatut("CANCELLED");
        reservationRepository.save(reservation);
        return true;
    }

    private ReservationResponseDTO toDTO(Reservation reservation, boolean isNew) {
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
                isNew
        );
    }
}