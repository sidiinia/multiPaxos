import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateServerSocket implements Runnable {

    private int port;
    private boolean running;
    private ServerSocket ss = null;
    private Map<Integer, List<Socket>> clientMap = new HashMap<>();

    public CreateServerSocket(int port) {
        this.port = port;
    }

    public void start() {
        try {
            ss = new ServerSocket(port);
        } catch (IOException e) {

        }
        running = true;
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {
            while (true) {
                Socket socket = ss.accept();
                System.out.println("accepted at port " + port + " " + socket);
                Client.incomingSockets.add(socket);
                //start another client thread for each server
                (new ServerClientThread(socket, clientMap.get(port))).start();

            }
        } catch(IOException e){

        }
    }
}