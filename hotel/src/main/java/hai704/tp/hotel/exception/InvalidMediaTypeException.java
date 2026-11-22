package hai704.tp.hotel.exception;

public class InvalidMediaTypeException extends RuntimeException {
    private final String expectedMediaType;
    private final String receivedMediaType;

    public InvalidMediaTypeException(String expected, String received) {
        super(String.format("Type de média non supporté. Attendu: %s, Reçu: %s", expected, received));
        this.expectedMediaType = expected;
        this.receivedMediaType = received;
    }

    public String getExpectedMediaType() { return expectedMediaType; }
    public String getReceivedMediaType() { return receivedMediaType; }
}