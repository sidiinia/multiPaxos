import java.io.Serializable;

public class Packet implements Serializable {


    private int ballotNum;

    private int acceptNum;

    private int acceptVal;

    private int numTicket;

    private String type;

    private int sender;



    public Packet(String type,int ballotNum, int acceptNum, int acceptVal, int sender) {
        this.type = type;
        this.ballotNum = ballotNum;
        this.acceptNum = acceptNum;
        this.acceptVal = acceptVal;
        this.sender = sender;

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


    public void printPacket() {
        System.out.println("THE PACKET: ");
        System.out.println("The type is " + getType());
        System.out.println("The ballot number is " + getBallotNum());
        System.out.println("The acceptNum is " + getAcceptNum());
        System.out.println("The acceptVal is " + getAcceptVal());
    }

}