import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
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
            ObjectInputStream inStream;
            try {
                //System.out.println("reading from " + clientSocket);
                inStream = new ObjectInputStream(clientSocket.getInputStream());
                packet = (Packet) inStream.readObject();

                //if receiving "Prepare", then leader election
                if(packet.getType().equals("Prepare")) {
                    System.out.println("received Prepare!");
                    if(packet.getBallotNum() >= Client.ballotNum) {
                        Client.ballotNum = packet.getBallotNum();
                        Packet ackPacket = new Packet("Ack", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
                        //ackPacket.printPacket();
                        Client.sendPacketToPort(ackPacket, packet.getSender());
                    }
                }
                //if receiving "Ack", the client agrees that I could be the leader
                else if(packet.getType().equals("Ack")) {
                    System.out.println("received Ack!");
                    if (Client.incrementCounter) {
                        Client.counter++;
                        if (Client.counter >= (int) Math.ceil((double) Client.portNums.size() + 1) / 2 - 1) {
                            System.out.println(Client.port + " has been elected leader!!!");
                            Client.leaderPid = Client.port;
                            Packet p = new Packet("SetLeader", 0, 0, 0, Client.port);
                            Client.sendPacketToAll(p);
                            Client.incrementCounter = false;
                            Client.counter = 0;
                        }
                    }
                }

                // if received "SetLeader", set leader to the port where the packet is coming from
                else if (packet.getType().equals("SetLeader")) {
                    System.out.println("received SetLeader");
                    Client.leaderPid = packet.getSender();
                }

                //if receiving "AcceptFromLeader", decide if I will accept this value or not
                else if(packet.getType().equals("AcceptFromLeader")) {
                    System.out.println("received AcceptFromLeader!");
                    if(packet.getBallotNum() >= Client.ballotNum) {
                        Client.ballotNum = packet.getBallotNum();
                        Client.acceptNum = packet.getBallotNum();
                        Client.acceptVal = packet.getAcceptVal();
                        Packet ackAcceptPacket = new Packet("AcceptFromAcceptor", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
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
                            Packet decisionPacket = new Packet("Decision", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
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
                    Packet acceptPacket = new Packet("AcceptFromLeader", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port);
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
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}