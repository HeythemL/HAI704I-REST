package hai704.tp.hotel.repository;

import hai704.tp.hotel.model.Offre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OffreRepository extends JpaRepository<Offre, String> {

    @Query("""
        SELECT o FROM Offre o
        WHERE o.ville = :ville
        AND o.dateDebut <= :dateFin
        AND o.dateFin >= :dateDebut
        AND o.capacite >= :nbPersonnes
        ORDER BY o.prixTotal ASC
    """)
    List<Offre> findByVilleAndDates(
            @Param("ville") String ville,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin,
            @Param("nbPersonnes") Integer nbPersonnes);

    Optional<Offre> findById(String id);
}