import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Semaphore;

import static java.lang.Thread.sleep;

public class Client {
    static int port;
    static int[] portNums;

    static volatile List<Socket> incomingSockets = new ArrayList<>();
    static volatile List<Socket> outgoingSockets = new ArrayList<>();


    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if (args.length < 1) {
            System.out.println("please specify port num");
            System.exit(0);
        }

        port = Integer.parseInt(args[0]);

        // read in config file which contains port num
        Scanner sc = new Scanner(new File("config.txt"));
        String line = sc.nextLine();
        sc.close();
        String[] ports = line.split(" ");
        portNums = new int[ports.length];
        for (int i = 0; i < ports.length; i++) {
            portNums[i] = Integer.parseInt(ports[i]);
        }

        CreateServerSocket ss = new CreateServerSocket(port);
        ss.start();

        // check if server exists, if exists, connect clients, else wait
        for (int i = 0; i < portNums.length; i++) {
            if (portNums[i] != port) {
                while (!serverListening("127.0.0.1", portNums[i])) {
                }
                Socket s = new Socket("127.0.0.1", portNums[i]);
                outgoingSockets.add(s);
            }
        }


        // wait for all the clients to come in
        while (incomingSockets.size() != 2 * (portNums.length - 1)) {
        }
        for (int i = 0; i < incomingSockets.size(); i++) {
            if (incomingSockets.get(i).isClosed()) {
                incomingSockets.remove(incomingSockets.get(i));
            }
        }

        //read
        for (int i = 0; i < incomingSockets.size(); i++) {
            ReadThread r1 = new ReadThread(incomingSockets.get(i));
            Thread t = new Thread(r1);
            t.start();
        }

    }


    public static boolean serverListening(String host, int port) {
        Socket s = null;
        try {
            s = new Socket(host, port);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (Exception e) {
                }
        }
    }
}


class ReadThread implements Runnable {
    Socket clientSocket;
    static Semaphore semaphore = new Semaphore(1);

    public ReadThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    public void run() {
        while (true) {
            Packet packet;
            ObjectInputStream inStream;
            try {
                //System.out.println("reading from " + clientSocket);
                inStream = new ObjectInputStream(clientSocket.getInputStream());
                packet = (Packet) inStream.readObject();
                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } catch (EOFException e) {
                //System.out.println("here");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
