import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Semaphore;

import static java.lang.Thread.sleep;

public class Client {
    static int port;
   // static int[] portNums;
    static ArrayList<Integer> portNums;

    static int leaderPid = 3000; // leader election
    static int counter = 0; // count ack back, to compare with majority
    static int counterAccept = 0; //leader counts how many accept received
    static boolean incrementCounterAccept = false; //flag to see if needs to increment counterAccept or not
    static int resTicket = 100;
    static List<Integer> log = new ArrayList<>();



    static volatile int ballotNum;
    static volatile int acceptNum;
    static volatile int acceptVal;


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
        //portNums = new int[ports.length];
        portNums = new ArrayList<>();
        for (int i = 0; i < ports.length; i++) {
            //portNums[i] = Integer.parseInt(ports[i]);
            portNums.add(Integer.parseInt(ports[i]));
        }

        CreateServerSocket ss = new CreateServerSocket(port);
        ss.start();

        // check if server exists, if exists, connect clients, else wait
        for (int i = 0; i < portNums.size(); i++) {
            if (portNums.get(i) != port) {
                while (!serverListening("127.0.0.1", portNums.get(i))) {
                }
                Socket s = new Socket("127.0.0.1", portNums.get(i));
                outgoingSockets.add(s);
            }
        }


        // wait for all the clients to come in
        while (incomingSockets.size() != 2 * (portNums.size() - 1)) {
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

/*
        //send heartbeat thread
        for(int i=0; i<outgoingSockets.size(); i++) {
            sendHeartbeatThread h1 = new sendHeartbeatThread(outgoingSockets.get(i));
            Thread t = new Thread(h1);
            t.start();
        }
*/

        for(int i=0; i<outgoingSockets.size(); i++) {
            try {
                sendHeartbeatThread h1 = new sendHeartbeatThread(outgoingSockets.get(i));
                Thread t = new Thread(h1);
                t.start();
            } catch (Exception e) {
                if(outgoingSockets.get(i).isClosed()) {
                    outgoingSockets.remove(outgoingSockets.get(i));
                    System.out.println("Lost the connection");
                }
            }
        }


        String clientCommand = "";
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        try {
            while (!clientCommand.equals("quit")) {
                clientCommand = br.readLine();
                String[] splitted = clientCommand.split("\\s+");
                if (splitted[0].equals("buy")) {
                    int numOfTickets = Integer.parseInt(splitted[1]);
                    // if i am the leader, send accept msg
                    if(Client.port == leaderPid) {
                        if(Client.resTicket < numOfTickets) {
                            System.out.println("The remaining tickets are not enough!");
                        }
                        else {
                            Client.ballotNum++;
                            Client.acceptNum = Client.ballotNum;
                            Client.acceptVal = numOfTickets;
                            Packet acceptPacket = new Packet("AcceptFromLeader", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
                            Client.incrementCounterAccept = true;
                            sendPacketToAll(acceptPacket);
                        }
                    }

                    //if i am not the leader, send msg to the leader
                    else {
                        if(Client.resTicket < numOfTickets) {
                            System.out.println("The remaining tickets are not enough!");
                        }
                        else {
                            Packet packet = new Packet("Request", Client.ballotNum, Client.acceptNum, numOfTickets, Client.port);
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



    public static void sendPrepare() {
        for (int i = 0; i < outgoingSockets.size(); i++) {
            Socket clientSocket = outgoingSockets.get(i);
            Packet packet = new Packet("Prepare", 0, 0, 0, port);

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
            e.printStackTrace();
        }
    }


    public static void sendPacketToAll(Packet packet) {
        for(int i = 0; i<outgoingSockets.size(); i++) {
            Socket clientSocket = outgoingSockets.get(i);

            try {
                ObjectOutputStream outStream = new ObjectOutputStream(clientSocket.getOutputStream());
                outStream.writeObject(packet);

            } catch (IOException e) {
                e.printStackTrace();
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


                //if receiving "Prepare", then leader election
                if(packet.getType().equals("Prepare")) {
                    if(packet.getBallotNum() >= Client.ballotNum) {
                        Client.ballotNum = packet.getBallotNum();
                        Packet ackPacket = new Packet("Ack", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
                        ackPacket.printPacket();
                        Client.sendPacketToAll(ackPacket);
                    }
                }
                //if receiving "Ack", the client agrees that I could be the leader
                else if(packet.getType().equals("Ack")) {
                    Client.counter++;
                    if(Client.counter >= (int)Math.ceil((double)Client.portNums.size()+1)/2 -1) {

                    }
                }

                //if receiving "AcceptFromLeader", decide if I will accept this value or not
                else if(packet.getType().equals("AcceptFromLeader")) {
                    System.out.println("receive AcceptFromLeader");
                    if(packet.getBallotNum() >= Client.ballotNum) {
                        Client.ballotNum = packet.getBallotNum();
                        Client.acceptNum = packet.getBallotNum();
                        Client.acceptVal = packet.getAcceptVal();
                        Packet ackAcceptPacket = new Packet("AcceptFromClient", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
                        ackAcceptPacket.printPacket();
                        Client.sendPacketToLeader(ackAcceptPacket);
                    }
                }

                //if receiving "AcceptFromClient" from the majority, leader will make a decision
                else if(packet.getType().equals("AcceptFromClient")) {
                    if(Client.incrementCounterAccept) {
                        Client.counterAccept++;
                        if(Client.counterAccept >= (int)Math.ceil((double)Client.portNums.size()+1)/2 -1) {
                            Packet decisionPacket = new Packet("Decision", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
                            decisionPacket.printPacket();
                            Client.sendPacketToAll(decisionPacket);
                            Client.log.add(packet.getAcceptVal()); // update leader's log
                            Client.resTicket -= packet.getAcceptVal();
                            Client.incrementCounterAccept = false;
                            Client.counterAccept = 0;
                        }

                    }

                }


                //if I am the leader, send "AcceptFromLeader" to others
                else if(packet.getType().equals("Request")) {
                    System.out.println("want to buy " + packet.getAcceptVal());
                    Client.ballotNum++; //increment leader's ballotnum
                    Client.acceptNum = Client.ballotNum;
                    Client.acceptVal = packet.getAcceptVal();
                    Packet acceptPacket = new Packet("AcceptFromLeader", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
                    acceptPacket.printPacket();
                    Client.incrementCounterAccept = true;
                    Client.sendPacketToAll(acceptPacket);
                }

                else if(packet.getType().equals("Decision")) {
                    System.out.println("receiving decision");
                    Client.log.add(packet.getAcceptVal());
                    Client.resTicket -= packet.getAcceptVal();
                }


                else if(packet.getType().equals("HeartBeat")) {
                    //System.out.println("RECEIVED A HEARTBEAT");

                }

                try {
                    sleep(1000);
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


class sendHeartbeatThread implements Runnable {
    Socket clientSocket;
    static Semaphore semaphore = new Semaphore(1);

    public sendHeartbeatThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        Packet packet = new Packet("HeartBeat", -1, -1, -1, Client.port);
        while (true) {
            try {
                sleep(3000);
                Client.sendPacket(clientSocket, packet);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}


