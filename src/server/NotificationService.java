package server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/** Invia notifiche asincrone via UDP (fine partita e nuova partita). */
public class NotificationService {

    private final DatagramSocket socket;

    public NotificationService() throws IOException {
        this.socket = new DatagramSocket();
    }

    public void notifyGameEnd(Game game, SessionManager sessionManager) {
        for (SessionInfo session : sessionManager.getAllSessions()) {
            String username = session.getUsername();
            PlayerGameState playerState = game.getPlayerState(username);
            if (playerState == null) continue;

            try {
                JsonObject notification = buildGameEndNotification(game, playerState);
                sendToClient(notification, session);
            } catch (IOException e) {
                System.err.println("[Notifica] Errore invio a " + username + ": " + e.getMessage());
            }
        }
    }

    public void notifyNewGame(Game game, SessionManager sessionManager) {
        for (SessionInfo session : sessionManager.getAllSessions()) {
            try {
                sendToClient(buildNewGameNotification(game), session);
            } catch (IOException e) {
                System.err.println("[Notifica] Errore invio nuova partita a "
                        + session.getUsername() + ": " + e.getMessage());
            }
        }
    }

    private JsonObject buildGameEndNotification(Game game, PlayerGameState playerState) {
        JsonObject json = new JsonObject();
        json.addProperty("notification", "gameEnd");
        json.addProperty("gameId", game.getGameId());

        JsonObject personalResults = new JsonObject();
        personalResults.addProperty("correctGroups", playerState.getCorrectCount());
        personalResults.addProperty("wrongAttempts", playerState.getWrongAttempts());
        personalResults.addProperty("score", playerState.getScore());
        personalResults.addProperty("status", playerState.getStatus().name());

        JsonArray groupsArray = new JsonArray();
        for (GameGroup group : game.getGroups()) {
            JsonObject groupJson = new JsonObject();
            groupJson.addProperty("theme", group.getTheme());
            JsonArray wordsArray = new JsonArray();
            for (String word : group.getWords()) wordsArray.add(word);
            groupJson.add("words", wordsArray);
            groupsArray.add(groupJson);
        }
        personalResults.add("allGroups", groupsArray);
        json.add("personalResults", personalResults);

        JsonObject gameStats = new JsonObject();
        gameStats.addProperty("participants", game.getParticipantCount());
        gameStats.addProperty("completed", game.getCompletedCount());
        gameStats.addProperty("winners", game.getWinnerCount());
        gameStats.addProperty("averageScore", Math.round(game.getAverageScore() * 100.0) / 100.0);
        json.add("gameStats", gameStats);

        return json;
    }

    private JsonObject buildNewGameNotification(Game game) {
        JsonObject json = new JsonObject();
        json.addProperty("notification", "newGame");
        json.addProperty("gameId", game.getGameId());
        json.addProperty("durationSeconds", game.getDurationMillis() / 1000);

        JsonArray wordsArray = new JsonArray();
        for (String word : game.getShuffledWords()) wordsArray.add(word);
        json.add("words", wordsArray);

        return json;
    }

    private void sendToClient(JsonObject json, SessionInfo session) throws IOException {
        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
        if (data.length > 65000) {
            System.err.println("[Notifica] Pacchetto troppo grande: " + data.length + " bytes");
            return;
        }
        DatagramPacket packet = new DatagramPacket(
                data, data.length, session.getUdpAddress(), session.getUdpPort());
        socket.send(packet);
    }

    public void close() {
        if (socket != null && !socket.isClosed()) socket.close();
    }
}
