package hu.u_szeged.inf.fog.simulator.agent.forecast;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ForecasterWorker {

    private final int port;

    private final Process process;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    
    public ForecasterWorker(int port, Process process) {
        this.port = port;
        this.process = process;
    }

    public int getPort() {
        return port;
    }

    public Process getProcess() {
        return process;
    }

    public void predict(String inputPath, String outputPath) throws IOException, InterruptedException {

        String json = "{"
                + "\"input_path\": \"" + inputPath + "\","
                + "\"output_path\": \"" + outputPath + "\""
                + "}";


        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/predict"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                "Worker on port " + port + " returned HTTP " + response.statusCode() + " body: " + response.body()
            );
        }
    }
}

