import java.io.Serializable;
import java.util.List;

public class Packet implements Serializable {


    private int ballotNum;

    private int acceptNum;

    private int acceptVal;

    private String type;

    private int sender;

    private List<String> pair;

    public Packet(String type,int ballotNum, int acceptNum, int acceptVal, int sender, List<String> pair) {
        this.type = type;
        this.ballotNum = ballotNum;
        this.acceptNum = acceptNum;
        this.acceptVal = acceptVal;
        this.sender = sender;
        this.pair = pair;
    }


    public String getType() {
        return type;
    }


    public int getSender() {
        return sender;
    }

    public int getBallotNum() {
        return ballotNum;
    }

    public int getAcceptNum() {
        return acceptNum;
    }

    public int getAcceptVal() {
        return acceptVal;
    }

    public List<String> getPair() { return pair; }

    public void printPacket() {
        System.out.println("THE PACKET: ");
        System.out.println("The type is " + getType());
        System.out.println("The ballot number is " + getBallotNum());
        System.out.println("The acceptNum is " + getAcceptNum());
        System.out.println("The acceptVal is " + getAcceptVal());
    }

}