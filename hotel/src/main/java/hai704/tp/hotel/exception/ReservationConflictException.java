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