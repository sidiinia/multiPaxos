import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.net.Socket;
import java.util.concurrent.Semaphore;

import static java.lang.Thread.sleep;

class ReadThread implements Runnable {
    Socket clientSocket;
    static Semaphore semaphore = new Semaphore(1);

    public ReadThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }


    public void run() {
        while (true) {
            Packet packet;
            try {
                //System.out.println("reading from " + clientSocket);
                ObjectInputStream inStream = new ObjectInputStream(clientSocket.getInputStream());
                packet = (Packet) inStream.readObject();

                //if receiving "Prepare", then leader election
                if(packet.getType().equals("Prepare")) {
                    System.out.println("received Prepare!");
                    if(packet.getBallotNum() > Client.ballotNum ||
                            (packet.getBallotNum() == Client.ballotNum && packet.getSender() > Client.port)) {
                        Client.ballotNum = packet.getBallotNum();
                        Packet ackPacket = new Packet("Ack", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port, Client.pair);
                        //ackPacket.printPacket();
                        Client.sendPacketToPort(ackPacket, packet.getSender());
                        //Client.leaderPid = packet.getSender();  // wrong
                    }
                }

                //if receiving "Ack", the client agrees that I could be the leader
                else if(packet.getType().equals("Ack")) {
                    System.out.println("received Ack!");
                    semaphore.acquire();
                    if (Client.incrementCounter) {
                        Client.counter++;
                        if (Client.counter >= (int) Math.ceil((double) Client.portNums.size() + 1) / 2 - 1) {
                            System.out.println(Client.port + " has been elected leader!!!");
                            Client.leaderPid = Client.port;
                            Packet p = new Packet("SetLeader", 0, 0, 0, Client.port, Client.pair);
                            Client.sendPacketToAll(p);
                            Client.incrementCounter = false;
                            Client.counter = 0;
                            //Client.phaseOneFinished = true;
                        }
                    }
                    semaphore.release();
                }

                else if (packet.getType().equals("SetLeader")) {
                    System.out.println("received SetLeader!");
                    Client.leaderPid = packet.getSender();
                    Packet p = new Packet("SetLeaderAck", 0, 0, 0, Client.port, Client.pair);
                    Client.sendPacketToLeader(p);
                }

                else if (packet.getType().equals("SetLeaderAck")) {
                    System.out.println("received SetLeaderAck!");
                    Client.phaseOneFinished = true;
                }

                //if receiving "AcceptFromLeader", decide if I will accept this value or not
                else if(packet.getType().equals("AcceptFromLeader")) {
                    System.out.println("received AcceptFromLeader!");
                    if(packet.getBallotNum() >= Client.ballotNum) {
                        Client.ballotNum = packet.getBallotNum();
                        Client.acceptNum = packet.getBallotNum();
                        Client.acceptVal = packet.getAcceptVal();
                        Packet ackAcceptPacket = new Packet("AcceptFromAcceptor", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port, Client.pair);
                        ackAcceptPacket.printPacket();
                        Client.sendPacketToLeader(ackAcceptPacket);
                    }
                }

                //if receiving "AcceptFromAcceptor" from the majority, leader will make a decision
                else if(packet.getType().equals("AcceptFromAcceptor")) {
                    System.out.println("received AcceptFromAcceptor!");
                    semaphore.acquire();
                    if(Client.incrementCounterAccept) {
                        Client.counterAccept++;
                        if(Client.counterAccept >= (int)Math.ceil((double)Client.portNums.size()+1)/2 -1) {
                            Packet decisionPacket = new Packet("Decision", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port, Client.pair);
                            decisionPacket.printPacket();
                            Client.sendPacketToAll(decisionPacket);
                            Client.log.add(packet.getAcceptVal()); // update leader's log
                            Client.resTicket -= packet.getAcceptVal();
                            Client.incrementCounterAccept = false;
                            Client.counterAccept = 0;
                        }
                    }
                    semaphore.release();
                }

                //if I am the leader, send "AcceptFromLeader" to others
                else if(packet.getType().equals("Request")) {
                    System.out.println("received Request!");
                    System.out.println(packet.getSender() + " want to buy " + packet.getAcceptVal());
                    Client.ballotNum++; //increment leader's ballotnum
                    Client.acceptNum = Client.ballotNum;
                    Client.acceptVal = packet.getAcceptVal();
                    Packet acceptPacket = new Packet("AcceptFromLeader", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port, Client.pair);
                    acceptPacket.printPacket();
                    Client.incrementCounterAccept = true;
                    Client.sendPacketToAll(acceptPacket);
                }

                else if(packet.getType().equals("Decision")) {
                    System.out.println("received Decision!");
                    Client.log.add(packet.getAcceptVal());
                    Client.resTicket -= packet.getAcceptVal();
                }


                else if(packet.getType().equals("HeartBeat")) {
                    //System.out.println("RECEIVED A HEARTBEAT");

                }

                // adding new config
                else if (packet.getType().equals("NewConfig")) {
                    System.out.println("RECEIVED NEWCONFIG");
                    Client.portNums.add(packet.getPair());
                    Socket socket = new Socket("127.0.0.1", packet.getSender());
                    Client.outgoingSockets.add(socket);

                    // start heartbeat
                    try {
                        sendHeartbeatThread h1 = new sendHeartbeatThread(socket);
                        Thread t = new Thread(h1);
                        t.start();
                    } catch (Exception e) {

                    }

                    // check leader???
                }

                else {
                    System.out.println("received an unknown packet");
                }

                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            } catch (EOFException e) {
                //System.out.println("here");
            } catch (StreamCorruptedException e) {

            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}