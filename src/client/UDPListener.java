package client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/** Thread dedicato alla ricezione di notifiche UDP dal server (fine partita, nuova partita). */
public class UDPListener implements Runnable {

    private final DatagramSocket socket;
    private volatile boolean running;
    private static final int BUFFER_SIZE = 65535;

    public UDPListener(int udpPort) throws IOException {
        this.socket = new DatagramSocket(udpPort);
        this.running = true;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String json = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                handleNotification(json);
            } catch (IOException e) {
                if (running) System.err.println("[UDP] Errore ricezione: " + e.getMessage());
            }
        }
    }

    private void handleNotification(String json) {
        try {
            JsonObject notification = JsonParser.parseString(json).getAsJsonObject();
            String type = notification.get("notification").getAsString();

            System.out.println();

            switch (type) {
                case "gameEnd": displayGameEnd(notification); break;
                case "newGame": displayNewGame(notification); break;
                default: System.out.println("[Notifica] Tipo sconosciuto: " + type);
            }

            System.out.print("> ");
            System.out.flush();
        } catch (Exception e) {
            System.err.println("[UDP] Errore parsing notifica: " + e.getMessage());
        }
    }

    private void displayGameEnd(JsonObject notification) {
        int gameId = notification.get("gameId").getAsInt();
        System.out.println("[Partita #" + gameId + " terminata]");

        if (notification.has("personalResults")) {
            JsonObject personal = notification.getAsJsonObject("personalResults");
            System.out.println("Stato: " + personal.get("status").getAsString()
                    + " | Corretti: " + personal.get("correctGroups").getAsInt()
                    + " | Errori: " + personal.get("wrongAttempts").getAsInt()
                    + " | Punteggio: " + personal.get("score").getAsInt());

            if (personal.has("allGroups")) {
                System.out.println("Soluzione:");
                JsonArray groups = personal.getAsJsonArray("allGroups");
                for (JsonElement groupElem : groups) {
                    JsonObject group = groupElem.getAsJsonObject();
                    System.out.print("  [" + group.get("theme").getAsString() + "] ");
                    JsonArray words = group.getAsJsonArray("words");
                    StringBuilder sb = new StringBuilder();
                    for (JsonElement w : words) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(w.getAsString());
                    }
                    System.out.println(sb.toString());
                }
            }
        }

        if (notification.has("gameStats")) {
            JsonObject stats = notification.getAsJsonObject("gameStats");
            System.out.println("Partecipanti: " + stats.get("participants").getAsInt()
                    + " | Completati: " + stats.get("completed").getAsInt()
                    + " | Vincitori: " + stats.get("winners").getAsInt()
                    + " | Media: " + stats.get("averageScore").getAsDouble());
        }
        System.out.println();
    }

    private void displayNewGame(JsonObject notification) {
        int gameId = notification.get("gameId").getAsInt();
        long duration = notification.get("durationSeconds").getAsLong();

        System.out.println("[Nuova partita #" + gameId + " - Durata: " + duration + "s]");
        System.out.println("Parole:");

        JsonArray words = notification.getAsJsonArray("words");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (JsonElement w : words) {
            if (count > 0) sb.append(" | ");
            sb.append(w.getAsString());
            count++;
            if (count % 4 == 0) {
                System.out.println("  " + sb.toString());
                sb = new StringBuilder();
                count = 0;
            }
        }
        if (sb.length() > 0) System.out.println("  " + sb.toString());
        System.out.println();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    public int getPort() { return socket.getLocalPort(); }
}
