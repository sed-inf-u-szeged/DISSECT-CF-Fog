package hu.u_szeged.inf.fog.simulator.prediction.communication;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import org.json.JSONObject;

public class ClientThread extends Thread {
    private Socket socket;
    private String name;
    private BufferedReader in;
    private DataOutputStream out;

    public ClientThread(Socket socket) {
        this.socket = socket;

        try {
            this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            this.out = new DataOutputStream(this.socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            SocketMessage message = sendAndGet(new SocketMessage("get-name", new JSONObject().put("message", "name")));
            this.name = message.getData().getString("name");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stopThread() {
        try {
            ServerSocket.CONNECTED_CLIENTS -= 1;
            sendAndGet(new SocketMessage("stop-connection", new JSONObject().put("message", "stop")));
            join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

    }

    public SocketMessage sendAndGet(SocketMessage message) {
        SocketMessage inMessage = null;
        try {
            PredictionLogger.info("socket-out", 
                    String.format("[%s]: %s", name == null ? "UNKNOWN" : name, message.getEvent()));

            SocketMessage messageSize = new SocketMessage("data-size", 
                    new JSONObject().put("size", message.toString().getBytes().length));
            out.write(createByteArrayMessage(messageSize));
            out.flush();

            String r = in.readLine(); // ACK for message size

            out.write(createByteArrayMessage(message));
            out.flush();
            String data = in.readLine();

            if (data == null) {
                PredictionLogger.warning("socket", "Message data is null.");
                join();
            }

            inMessage = new SocketMessage(data);
            PredictionLogger.info("socket-in", 
                    String.format("[%s]: %s", name == null ? "UNKNOWN" : name, inMessage.getEvent()));
        } catch (Exception e) {
            //e.printStackTrace();
        }

        return inMessage;
    }

    public String getSocketName() {
        return name;
    }

    private static byte[] createByteArrayMessage(SocketMessage message) {
        return message.toString().getBytes();
    }
}
