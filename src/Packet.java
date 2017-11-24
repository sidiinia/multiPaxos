import java.io.Serializable;

public class Packet implements Serializable {

    private final String message;

    private final int port;

    private int money;

    private int type;

    private int sender;

    private int markerCounter;

    public Packet(int type, String message, int port, int money, int sender, int markerCounter) {
        this.type = type;
        this.message = message;
        this.port = port;
        this.money = money;
        this.sender = sender;
        this.markerCounter = markerCounter;
    }



    @Override
    public String toString() {
        return String.format("Packet [message=%s", message);
    }
}