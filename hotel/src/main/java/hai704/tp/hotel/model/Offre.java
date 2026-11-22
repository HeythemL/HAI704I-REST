package hai704.tp.hotel.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "offres")
public class Offre {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "nom_hotel", nullable = false, length = 200)
    private String nomHotel;

    @Column(name = "ville", nullable = false, length = 100)
    private String ville;

    @Column(name = "type_chambre", nullable = false, length = 100)
    private String typeChambre;

    @Column(name = "capacite", nullable = false)
    private Integer capacite;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "prix_total", nullable = false)
    private Double prixTotal;

    @Column(name = "photo", columnDefinition = "BLOB")
    private byte[] photo;

    public Offre() {}

    public Offre(String id, String nomHotel, String ville, String typeChambre,
                 Integer capacite, LocalDate dateDebut, LocalDate dateFin,
                 Double prixTotal, byte[] photo) {
        this.id = id;
        this.nomHotel = nomHotel;
        this.ville = ville;
        this.typeChambre = typeChambre;
        this.capacite = capacite;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.prixTotal = prixTotal;
        this.photo = photo;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNomHotel() { return nomHotel; }
    public void setNomHotel(String nomHotel) { this.nomHotel = nomHotel; }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }

    public String getTypeChambre() { return typeChambre; }
    public void setTypeChambre(String typeChambre) { this.typeChambre = typeChambre; }

    public Integer getCapacite() { return capacite; }
    public void setCapacite(Integer capacite) { this.capacite = capacite; }

    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }

    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }

    public Double getPrixTotal() { return prixTotal; }
    public void setPrixTotal(Double prixTotal) { this.prixTotal = prixTotal; }

    public byte[] getPhoto() { return photo; }
    public void setPhoto(byte[] photo) { this.photo = photo; }
}