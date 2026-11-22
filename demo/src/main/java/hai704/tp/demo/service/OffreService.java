package hai704.tp.demo.service;

import hai704.tp.demo.dto.OffreRequestDTO;
import hai704.tp.demo.dto.OffreResponseDTO;
import hai704.tp.demo.dto.OffreDTO;
import hai704.tp.demo.dto.LinkDTO;
import hai704.tp.demo.exception.OffreNotFoundException;
import hai704.tp.demo.model.Offre;
import hai704.tp.demo.repository.OffreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class OffreService {

    private final OffreRepository offreRepository;

    public OffreService(OffreRepository offreRepository) {
        this.offreRepository = offreRepository;
    }

    public OffreResponseDTO rechercher(OffreRequestDTO criteria) {
        if (!criteria.isDateRangeValid()) {
            throw new IllegalArgumentException("La date de début doit être antérieure ou égale à la date de fin");
        }

        List<Offre> offres = offreRepository.findByVilleAndDates(
                criteria.ville(),
                criteria.dateDebut(),
                criteria.dateFin(),
                criteria.nbPersonnes()
        );

        List<OffreDTO> offreDTOs = offres.stream()
                .map(this::toDTO)
                .toList();

        Map<String, LinkDTO> collectionLinks = new HashMap<>();
        collectionLinks.put("self", LinkDTO.self("/api/v1/offres"));

        return new OffreResponseDTO(offreDTOs, collectionLinks, offres.size());
    }

    public Optional<Offre> findById(String id) {
        return offreRepository.findById(id);
    }

    public Optional<OffreResponseDTO> findByIdAsDTO(String id) {
        return offreRepository.findById(id)
                .map(this::toDTO)
                .map(dto -> new OffreResponseDTO(List.of(dto), Map.of("self", LinkDTO.self("/api/v1/offres/" + id)), 1));
    }

    public List<Offre> findAll() {
        return offreRepository.findAll();
    }

    public Optional<byte[]> getPhoto(String id) {
        return offreRepository.findById(id)
                .map(Offre::getPhoto)
                .filter(photo -> photo != null && photo.length > 0);
    }

    @Transactional
    public Offre save(Offre offre) {
        return offreRepository.save(offre);
    }

    private OffreDTO toDTO(Offre offre) {
        Map<String, LinkDTO> links = new HashMap<>();
        links.put("self", LinkDTO.self("/api/v1/offres/" + offre.getId()));
        links.put("photo", LinkDTO.photo("/api/v1/offres/" + offre.getId() + "/photo"));
        links.put("reserver", LinkDTO.reserver("/api/v1/reservations"));

        return new OffreDTO(
                offre.getId(),
                offre.getNomHotel(),
                offre.getVille(),
                offre.getTypeChambre(),
                offre.getCapacite(),
                offre.getPrixTotal(),
                offre.getDateDebut().toString(),
                offre.getDateFin().toString(),
                links
        );
    }

    // Méthode utilitaire pour initialiser des données de test
    @Transactional(readOnly = false)
    public void initSampleData() {
        if (offreRepository.count() == 0) {
            Offre o1 = new Offre("H1-1", "Grand Hôtel Paris", "Paris", "Double Deluxe", 2,
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), 240.0, new byte[0]);
            Offre o2 = new Offre("H1-2", "Grand Hôtel Paris", "Paris", "Suite", 4,
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), 480.0, new byte[0]);
            Offre o3 = new Offre("H2-1", "Hôtel Lyon Centre", "Lyon", "Simple", 1,
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), 90.0, new byte[0]);
            offreRepository.saveAll(List.of(o1, o2, o3));
        }
    }
}