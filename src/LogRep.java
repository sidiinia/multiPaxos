import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class LogRep implements Serializable {

    private Map<Integer, String> log;
    private String[] leaderPid;
    private String message;
    private int remainingTickets;
    private int firstUnchosenIndex;

    public LogRep(Map<Integer, String> log, String[] leaderPid, String message, int remainingTickets, int firstUnchosenIndex) {
        this.log = log;
        this.leaderPid = leaderPid;
        this.message = message;
        this.remainingTickets = remainingTickets;
        this.firstUnchosenIndex = firstUnchosenIndex;
    }

    public Map<Integer, String> getLog() {
        return log;
    }

    public String[] getLeaderPid() {
        return leaderPid;
    }

    public int getRemainingTickets() {
        return remainingTickets;
    }

    public String getMessage() {
        return message;
    }

    public int getFirstUnchosenIndex() {
        return firstUnchosenIndex;
    }
}