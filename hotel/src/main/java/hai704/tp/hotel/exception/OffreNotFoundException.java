package hai704.tp.hotel.exception;

public class OffreNotFoundException extends RuntimeException {
    private final String resourceId;

    public OffreNotFoundException(String resourceId) {
        super("Offre introuvable : " + resourceId);
        this.resourceId = resourceId;
    }

    public String getResourceId() { return resourceId; }
}