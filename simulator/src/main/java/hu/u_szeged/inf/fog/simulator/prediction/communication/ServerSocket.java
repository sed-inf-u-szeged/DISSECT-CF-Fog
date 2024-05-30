package hu.u_szeged.inf.fog.simulator.prediction.communication;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.ElectronLauncher;
import hu.u_szeged.inf.fog.simulator.prediction.communication.launchers.Launcher;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PredictorTemplate;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;

import org.json.JSONObject;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ServerSocket {
    private static ServerSocket SERVER_SOCKET;
    public static int CONNECTED_CLIENTS = 0;
    private java.net.ServerSocket server;
    private int port;
    private List<ClientThread> clientThreads;

    private ServerSocket(int port) {
        this.port = port;
        this.clientThreads = new ArrayList<>();
    }

    public static ServerSocket getInstance() {
        if (ServerSocket.SERVER_SOCKET == null) {
            ServerSocket.SERVER_SOCKET = new ServerSocket(65432);
        }
        return ServerSocket.SERVER_SOCKET;
    }

    public void waitForConnections(List<Launcher> applications) {
        try {
            while (CONNECTED_CLIENTS != applications.size()) {
                PredictionLogger.info("ServerSocket", String.format("Waiting for connection... (%s / %s)", CONNECTED_CLIENTS, applications.size()));
                Socket socket = server.accept();
                PredictionLogger.info("ServerSocket", "Socket connected!");

                ClientThread clientThread = new ClientThread(socket);
                clientThread.start();
                clientThreads.add(clientThread);

                CONNECTED_CLIENTS += 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        /*while (true) {
            int c = 0;
            for (ClientThread clientThread: clientThreads) {
                c += clientThread.getSocketName() == null ? 0 : 1;
            }

            if (c == clientThreads.size()) {
                break;
            }
        }*/

        if (Launcher.hasApplication(ElectronLauncher.class.getSimpleName())) {
            try {
                sendAndGet(
                        SocketMessage.SocketApplication.APPLICATION_INTERFACE,
                        new SocketMessage("set-ui-predictors", PredictorTemplate.getAllAsJSON())
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void stopThreads() {
        for (ClientThread clientThread: clientThreads) {
            clientThread.stopThread();
        }

        clientThreads = new ArrayList<>();
        CONNECTED_CLIENTS = 0;
    }

    public void start() {
        try {
            server = new java.net.ServerSocket(port);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void waitForPredictionSettings() {
        PredictionLogger.info("ServerSocket", "Waiting for prediction settings...");
        if (Launcher.hasApplication(ElectronLauncher.class.getSimpleName())) {
            try {
                SocketMessage message = sendAndGet(
                        SocketMessage.SocketApplication.APPLICATION_INTERFACE,
                        new SocketMessage("get-simulation-settings", new JSONObject().put("get", "settings"))
                );
                SimulationSettings.set(new SimulationSettings(message.getData().getJSONObject("simulation-settings")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        PredictionLogger.info("ServerSocket", "Prediction settings have been arrive!");

        try {
            sendAndGet(
                    SocketMessage.SocketApplication.APPLICATION_PREDICTOR,
                    new SocketMessage("simulation-settings", new JSONObject().put("simulation-settings", SimulationSettings.get().toJSON()))
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            try {
                server.close();
                server = null;
                stopThreads();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ClientThread getClientThreadByApplication(SocketMessage.SocketApplication application) {
        for (ClientThread clientThread: clientThreads) {
            if (clientThread.getSocketName().equals(application.value)) {
                return clientThread;
            }
        }
        return null;
    }

    public SocketMessage sendAndGet(SocketMessage.SocketApplication application, SocketMessage message) throws Exception {
        ClientThread clientThread = getClientThreadByApplication(application);
        return clientThread.sendAndGet(message);
    }
}
