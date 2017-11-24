import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

class ServerClientThread extends Thread {
    Socket socket;
    List<Socket> clientList;
    boolean running = true;
    Packet packet;

    ServerClientThread(Socket socket, List<Socket> clientList){
        this.socket = socket;
        this.clientList = clientList;
    }
    public void run(){
        /*try{
            ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
            while (running) {
                packet = (Packet) inStream.readObject();
                System.out.println("server received: " + packet.getMessage());

                for (int i = 0; i < clientList.size(); i++) {
                    if (!clientList.get(i).equals(socket)) {
                        ObjectOutputStream outStream = new ObjectOutputStream(clientList.get(i).getOutputStream());
                        outStream.writeObject(packet);
                    }
                }
            }
            //inStream.close();
            //outStream.close();
            socket.close();
        }catch(Exception e){
            System.out.println(e.getStackTrace());
        } finally{
            System.out.println("Client -" + " exit!! ");
        }*/
    }
}
