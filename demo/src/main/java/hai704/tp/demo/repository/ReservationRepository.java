package hai704.tp.demo.repository;

import hai704.tp.demo.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

    Optional<Reservation> findById(String id);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM Reservation r
        WHERE r.offreId = :offreId
        AND r.statut = 'CONFIRMED'
        AND r.dateFin >= :dateDebut
        AND r.dateDebut <= :dateFin
    """)
    boolean existsOverlapping(
            @Param("offreId") String offreId,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin);

    List<Reservation> findByOffreId(String offreId);
}