package hai704.tp.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "offre_id", nullable = false, length = 50)
    private String offreId;

    @Column(name = "nom_client", nullable = false, length = 200)
    private String nomClient;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "CONFIRMED";

    @Column(name = "prix_paye", nullable = false)
    private Double prixPaye;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructeurs
    public Reservation() {}

    public Reservation(String id, String offreId, String nomClient,
                       LocalDate dateDebut, LocalDate dateFin,
                       Double prixPaye, String idempotencyKey) {
        this.id = id;
        this.offreId = offreId;
        this.nomClient = nomClient;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.prixPaye = prixPaye;
        this.idempotencyKey = idempotencyKey;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOffreId() { return offreId; }
    public void setOffreId(String offreId) { this.offreId = offreId; }

    public String getNomClient() { return nomClient; }
    public void setNomClient(String nomClient) { this.nomClient = nomClient; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public Double getPrixPaye() { return prixPaye; }
    public void setPrixPaye(Double prixPaye) { this.prixPaye = prixPaye; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    @Version
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Helper pour ETag (concurrence optimiste)
    public String computeETag() {
        return "\"" + version + "-" + id.hashCode() + "\"";
    }

    // Vérification chevauchement dates
    public boolean overlaps(LocalDate otherDebut, LocalDate otherFin) {
        return !this.dateFin.isBefore(otherDebut) && !this.dateDebut.isAfter(otherFin);
    }
}