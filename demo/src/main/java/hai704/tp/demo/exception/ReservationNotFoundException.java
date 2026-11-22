package hai704.tp.demo.exception;

public class ReservationNotFoundException extends RuntimeException {
    private final String resourceId;

    public ReservationNotFoundException(String resourceId) {
        super("Réservation introuvable : " + resourceId);
        this.resourceId = resourceId;
    }

    public String getResourceId() { return resourceId; }
}