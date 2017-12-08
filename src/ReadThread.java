import java.io.*;
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
            Packet packet = null;
            LogRep logRep = null;
            try {
                //System.out.println("reading from " + clientSocket);
                ObjectInputStream inStream = new ObjectInputStream(clientSocket.getInputStream());
                Object obj = inStream.readObject();

                if(obj instanceof Packet) {
                    packet = (Packet) obj;
                    //if receiving "Prepare", then leader election
                    if(packet.getType().equals("Prepare")) {
                        System.out.println("received Prepare!");
                        if(packet.getBallotNum() > Client.ballotNum ||
                                (packet.getBallotNum() == Client.ballotNum && packet.getSender() > Client.port)) {
                            Client.ballotNum = packet.getBallotNum();
                            Packet ackPacket = new Packet("Ack", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port, Client.pair, -1);
                            //ackPacket.printPacket();
                            Client.sendPacketToPort(ackPacket, packet.getPair());
                            //Client.leaderPid = packet.getSender();  // wrong
                        }
                    }

                    //if receiving "Ack", the client agrees that I could be the leader
                    else if(packet.getType().equals("Ack")) {
                        System.out.println("received Ack!");
                        semaphore.acquire();
                        if (Client.incrementCounter) {
                            Client.counter++;
                            if (Client.counter >= Client.quorumSize - 1) {
                                System.out.println(Client.port + " has been elected leader!!!");
                                Client.leaderPid[0] = Client.host;
                                Client.leaderPid[1] = Integer.toString(Client.port);
                                Packet p = new Packet("SetLeader", 0, 0, 0, Client.port, Client.pair, -1);
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
                        Client.leaderPid[0] = packet.getPair().get(0);
                        Client.leaderPid[1] = Integer.toString(packet.getSender());
                        Packet p = new Packet("SetLeaderAck", 0, 0, 0, Client.port, Client.pair, -1);
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
                            Client.firstUnchosenIndex = packet.getIndex()+1;
                            Packet ackAcceptPacket = new Packet("AcceptFromAcceptor", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port, Client.pair, -1);
                            //ackAcceptPacket.printPacket();
                            Client.sendPacketToLeader(ackAcceptPacket);
                        }
                    }

                    //if receiving "AcceptFromAcceptor" from the majority, leader will make a decision
                    else if(packet.getType().equals("AcceptFromAcceptor")) {
                        System.out.println("received AcceptFromAcceptor!");
                        semaphore.acquire();
                        if(Client.incrementCounterAccept) {
                            Client.counterAccept++;
                            if(Client.counterAccept >= Client.quorumSize-1) {
                                Packet decisionPacket = new Packet("Decision", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port, Client.pair, -1);
                                //decisionPacket.printPacket();
                                Client.sendPacketToAll(decisionPacket);
                                Client.log.put(Client.firstUnchosenIndex - 1,"Sold "+ Integer.toString(packet.getAcceptVal())+ " tickets"); // update leader's log
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
                        Packet acceptPacket = new Packet("AcceptFromLeader", Client.ballotNum, Client.acceptNum, Client.acceptVal, Client.port, Client.pair, Client.firstUnchosenIndex);
                        Client.firstUnchosenIndex++;
                        //acceptPacket.printPacket();
                        Client.incrementCounterAccept = true;
                        Client.sendPacketToAll(acceptPacket);
                    }

                    else if(packet.getType().equals("Decision")) {
                        System.out.println("received Decision!");
                        Client.log.put(Client.firstUnchosenIndex - 1, "Sold "+Integer.toString(packet.getAcceptVal()) + " tickets");
                        Client.resTicket -= packet.getAcceptVal();
                    }


                    else if(packet.getType().equals("HeartBeat")) {
                        //System.out.println("RECEIVED A HEARTBEAT");

                    }

                    // adding new config
                    else if (packet.getType().equals("NewConfig")) {
                        System.out.println("RECEIVED NEWCONFIG");
                        Client.portNums.add(packet.getPair());
                        Socket socket = new Socket(packet.getPair().get(0), Integer.parseInt(packet.getPair().get(1)));
                        Client.outgoingSockets.add(socket);

                        //add config change to the log
                        Client.log.put(packet.getIndex(),"Config Change - ADD "+packet.getPair().get(0)+"-"+packet.getPair().get(1));
                        Client.firstUnchosenIndex = packet.getIndex() + 1;

                        // start heartbeat
                        try {
                            sendHeartbeatThread h1 = new sendHeartbeatThread(socket);
                            Thread t = new Thread(h1);
                            t.start();
                        } catch (Exception e) {

                        }

                        // check leader???
                        //If I am the leader, send out current status of the log to the newly connected machine
                        if(Client.host.equals(Client.leaderPid[0]) &&
                                Client.pair.get(1).equals(Client.leaderPid[1])) {
                            ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
                            LogRep log = new LogRep(Client.log, Client.leaderPid, "log", Client.resTicket);
                            outStream.writeObject(log);
                        }

                    }


                }
                else if(obj instanceof  LogRep) {
                    logRep = (LogRep) obj;
                    if (logRep.getMessage().equals("log")) {
                        System.out.println("received log!");
                        Client.log = logRep.getLog();
                        Client.leaderPid = logRep.getLeaderPid();
                        Client.resTicket = logRep.getRemainingTickets();

                    }
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