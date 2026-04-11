package com.keyremapper.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Streaming HTTP client for the Ollama local LLM API.
 * Configured via environment variables OLLAMA_HOST and OLLAMA_MODEL.
 */
public class OllamaClient {

    private final String host;
    private final String model;
    private final HttpClient client;
    private final Gson gson = new Gson();

    public OllamaClient() {
        String h = System.getenv("OLLAMA_HOST");
        this.host = (h != null && !h.isEmpty()) ? h : "http://localhost:11434";
        String m = System.getenv("OLLAMA_MODEL");
        this.model = (m != null && !m.isEmpty()) ? m : "gemma3";
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String getModel() { return model; }

    /** Pings Ollama to check if the server is reachable. */
    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(host + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /* ================================================================== */
    /*  Message type used by the /api/chat endpoint                       */
    /* ================================================================== */

    public static class Message {
        public final String role;
        public final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /* ================================================================== */
    /*  Streaming chat                                                    */
    /* ================================================================== */

    /**
     * Sends a chat request and streams the response token-by-token.
     * This method blocks until the response is complete.
     *
     * @param messages  conversation history (system + user + assistant)
     * @param onToken   called on each token as it arrives (may be null)
     * @return the full accumulated response text
     */
    public String streamChat(List<Message> messages, Consumer<String> onToken)
            throws IOException, InterruptedException {

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", gson.toJsonTree(messages));
        body.addProperty("stream", true);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(host + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<InputStream> resp =
                client.send(req, HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() != 200) {
            throw new IOException("Ollama returned HTTP " + resp.statusCode());
        }

        StringBuilder full = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(resp.body()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    JsonObject obj = gson.fromJson(line, JsonObject.class);
                    if (obj.has("message")) {
                        JsonObject msg = obj.getAsJsonObject("message");
                        if (msg.has("content")) {
                            String token = msg.get("content").getAsString();
                            full.append(token);
                            if (onToken != null) onToken.accept(token);
                        }
                    }
                    if (obj.has("done") && obj.get("done").getAsBoolean()) {
                        break;
                    }
                } catch (com.google.gson.JsonSyntaxException ignored) { }
            }
        }
        return full.toString();
    }
}
