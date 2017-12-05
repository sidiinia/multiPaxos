import java.io.Serializable;
import java.util.List;

public class LogRep implements Serializable {

    private List<String> log;
    private String[] leaderPid;
    private String message;
    private int remainingTickets;

    public LogRep(List<String> log, String[] leaderPid, String message, int remainingTickets) {
        this.log = log;
        this.leaderPid = leaderPid;
        this.message = message;
        this.remainingTickets = remainingTickets;
    }

    public List<String> getLog() {
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
}