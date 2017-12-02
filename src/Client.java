import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Semaphore;

import static java.lang.Thread.sleep;

public class Client {
    static String host;
    static int port;
    static List<String> pair;
    //static int[] portNums;
    //static ArrayList<Integer> portNums = new ArrayList<>();
    static List<List<String>> portNums = new ArrayList<>();

    static int leaderPid; // leader election
    static int counter = 0; // count ack back, to compare with majority
    static boolean incrementCounter = true;
    static int counterAccept = 0; //leader counts how many accept received
    static boolean incrementCounterAccept = false; //flag to see if needs to increment counterAccept or not
    static int resTicket = 100;
    static List<Integer> log = new ArrayList<>();
    //static List<Socket> liveCenter = new ArrayList<>(); // live center to check which servers are alive


    static volatile int ballotNum;
    static volatile int acceptNum;
    static volatile int acceptVal;

    static volatile boolean phaseOneFinished = false;

    static volatile List<Socket> incomingSockets = new ArrayList<>();
    static volatile List<Socket> outgoingSockets = new ArrayList<>();


    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if (args.length < 2) {
            System.out.println("please specify host ip and port num");
            System.exit(0);
        }

        // read in config file which contains port num
        Scanner sc = new Scanner(new File("config.txt"));
        String line = sc.nextLine();
        sc.close();
        String[] ports = line.split(" ");
        //portNums = new int[ports.length];
        for (int i = 0; i < ports.length; i++) {
            //portNums[i] = Integer.parseInt(ports[i]);
            String[] pair = ports[i].split("-");
            portNums.add(Arrays.asList(pair));
        }

        host = args[0];
        port = Integer.parseInt(args[1]);
        pair = Arrays.asList(args);
        // if port is in config file, it is old config
        if (portNums.contains(pair)) {
            CreateServerSocket ss = new CreateServerSocket(port);
            ss.start();

            // check if server exists, if exists, connect clients, else wait
            for (int i = 0; i < portNums.size(); i++) {
                if (!portNums.get(i).equals(pair)) {
                    while (!serverListening(portNums.get(i).get(0), Integer.parseInt(portNums.get(i).get(1)))) {
                    }
                    //Socket s = new Socket("127.0.0.1", portNums.get(i));
                    //outgoingSockets.add(s);
                }
            }
        }

        // if port is new config
        else {
            CreateServerSocket ss = new CreateServerSocket(port);
            ss.start();
            for (int i = 0; i < portNums.size(); i++) {
                Socket s = new Socket(portNums.get(i).get(0), Integer.parseInt(portNums.get(i).get(1)));
                outgoingSockets.add(s);
            }
            Packet p = new Packet("NewConfig", 0, 0, 0, port, pair);
            sendPacketToAll(p);

        }


        // wait for all the clients to come in
        //while (incomingSockets.size() != 2 * (portNums.size() - 1)) {
        //}
        while (incomingSockets.size() != portNums.size() -1) {}
        for (int i = 0; i < incomingSockets.size(); i++) {
            if (incomingSockets.get(i).isClosed()) {
                incomingSockets.remove(incomingSockets.get(i));
            }
        }

        //read
        /*for (int i = 0; i < incomingSockets.size(); i++) {
            ReadThread r1 = new ReadThread(incomingSockets.get(i));
            Thread t = new Thread(r1);
            t.start();
        }*/
/*
        //send heartbeat thread
        for(int i=0; i<outgoingSockets.size(); i++) {
            sendHeartbeatThread h1 = new sendHeartbeatThread(outgoingSockets.get(i));
            Thread t = new Thread(h1);
            t.start();
        }
*/

        // send heartbeat
        for(int i=0; i<outgoingSockets.size(); i++) {
            try {
                sendHeartbeatThread h1 = new sendHeartbeatThread(outgoingSockets.get(i));
                Thread t = new Thread(h1);
                t.start();
            } catch (Exception e) {

            }
        }

        // take user command line input
        String clientCommand = "";
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        try {
            while (!clientCommand.equals("quit")) {
                clientCommand = br.readLine();
                String[] splitted = clientCommand.split("\\s+");
                if (splitted[0].equals("buy")) {

                    // check if leader exists
                    if (leaderPid == 0) {
                        ballotNum++;
                        Packet packet = new Packet("Prepare", ballotNum, acceptNum, acceptVal, port, pair);
                        sendPacketToAll(packet);
                        while (!phaseOneFinished || leaderPid == 0) {}
                    }

                    int numOfTickets = Integer.parseInt(splitted[1]);
                    // if i am the leader, send accept msg
                    if(port == leaderPid) {
                        if(resTicket < numOfTickets) {
                            System.out.println("The remaining tickets are not enough!");
                        }
                        else {
                            ballotNum++;
                            acceptNum = ballotNum;
                            acceptVal = numOfTickets;
                            Packet acceptPacket = new Packet("AcceptFromLeader", ballotNum, acceptNum, acceptVal, port, pair);
                            incrementCounterAccept = true;
                            sendPacketToAll(acceptPacket);
                        }
                    }

                    //if i am not the leader, send msg to the leader
                    else {
                        if(resTicket < numOfTickets) {
                            System.out.println("The remaining tickets are not enough!");
                        }
                        else {
                            Packet packet = new Packet("Request", ballotNum, acceptNum, numOfTickets, port, pair);
                            sendPacketToLeader(packet);
                        }
                    }
                }
                else if (splitted[0].equals("show")) {
                    //show the state of the state machine
                    //show the committed logs
                    System.out.println("Remaining tickets " + Client.resTicket);
                    System.out.println("The log: ");
                    for(int i=0; i<log.size(); i++) {
                        System.out.print(log.get(i) + " ");
                    }
                    System.out.println();

                }

                else if (splitted[0].equals("leader")) {
                    System.out.println("Current leader is " + leaderPid);
                }

                else {

                }
            }
        } catch (IOException e) {

        }


    }


    public static boolean serverListening(String host, int port) {
        Socket s = null;
        try {
            s = new Socket(host, port);
            Client.outgoingSockets.add(s);
            return true;
        } catch (Exception e) {
            return false;
        } /*finally {
            if (s != null)
                try {
                    s.close();
                } catch (Exception e) {
                }
        }*/
    }



    public static void sendPrepare() {
        for (int i = 0; i < outgoingSockets.size(); i++) {
            Socket clientSocket = outgoingSockets.get(i);
            Packet packet = new Packet("Prepare", 0, 0, 0, port, pair);

            try {
                ObjectOutputStream outStream = new ObjectOutputStream(clientSocket.getOutputStream());
                outStream.writeObject(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    public static void sendPacket(Socket socket , Packet packet) {

        try {
            ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
            outStream.writeObject(packet);

        } catch (IOException e) {
            List<String> pair = Arrays.asList(socket.getInetAddress().getHostAddress(), String.valueOf(socket.getPort()));
            if(Client.portNums.contains(pair)) {
                portNums.remove(pair);
                outgoingSockets.remove(socket);
                System.out.println("Removing " + socket.getPort());
                //System.out.println("The size of portNum is " + Client.portNums.size());

                if (socket.getPort() == Client.leaderPid) {
                    System.out.println("detected leader failure, restart phase 1");
                    ballotNum++;
                    Packet p = new Packet("Prepare", ballotNum, acceptNum, acceptVal, port, pair);
                    sendPacketToAll(p);
                } else {
                    System.out.println("detected non-leader failure");
                }
            }
        }
    }


    public static void sendPacketToAll(Packet packet) {
        for(int i = 0; i<outgoingSockets.size(); i++) {
            Socket clientSocket = outgoingSockets.get(i);

            sendPacket(clientSocket, packet);
        }
    }

    public static void sendPacketToPort(Packet packet, int port) {
        for (int i = 0; i < outgoingSockets.size(); i++) {
            Socket socket = outgoingSockets.get(i);
            if (socket.getPort() == port) {
                sendPacket(socket, packet);
            }
        }
    }

    public static void sendPacketToLeader(Packet packet) {
        for(int i=0; i<outgoingSockets.size(); i++) {
            Socket leaderSocket = outgoingSockets.get(i);
            if(leaderSocket.getPort() == leaderPid) {
                sendPacket(leaderSocket, packet);
            }
        }

    }



}

class sendHeartbeatThread implements Runnable {
    Socket clientSocket;
    public static volatile boolean flag = true;

    static Semaphore semaphore = new Semaphore(1);

    public sendHeartbeatThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        Packet packet = new Packet("HeartBeat", -1, -1, -1, Client.port, Client.pair);
        while (flag) {
            try {
                sleep(3000);
                Client.sendPacket(clientSocket, packet);
            } catch (InterruptedException e) {
                flag = false;
                e.printStackTrace();
            }
        }
    }
}


