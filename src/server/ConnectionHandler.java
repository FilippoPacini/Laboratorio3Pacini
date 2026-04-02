package server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Gestisce una singola connessione TCP con un client.
 * Ogni messaggio usa framing length-prefixed: 4 byte (int big-endian) + payload JSON.
 */
public class ConnectionHandler implements Runnable {

    private final Socket socket;
    private final RequestHandler requestHandler;
    private final SessionManager sessionManager;
    private final GameManager gameManager;

    private String loggedInUsername;
    private DataInputStream in;
    private DataOutputStream out;

    public ConnectionHandler(Socket socket, RequestHandler requestHandler,
                             SessionManager sessionManager, GameManager gameManager) {
        this.socket = socket;
        this.requestHandler = requestHandler;
        this.sessionManager = sessionManager;
        this.gameManager = gameManager;
        this.loggedInUsername = null;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            System.out.println("[Connection] Nuova connessione da " + socket.getRemoteSocketAddress());

            // loop: leggo un messaggio, lo processo, e mando la risposta
            // dopo ogni risposta gestisco eventuali cambi di stato (login, logout, cambio username)
            while (!socket.isClosed()) {
                String jsonRequest = readMessage();
                if (jsonRequest == null) break;

                String jsonResponse = requestHandler.handle(jsonRequest, loggedInUsername, null);
                jsonResponse = handlePostLogin(jsonRequest, jsonResponse);
                handlePostLogout(jsonRequest, jsonResponse);
                handlePostUpdateCredentials(jsonRequest, jsonResponse);
                sendMessage(jsonResponse);
            }

        } catch (IOException e) {
            System.out.println("[Connection] Connessione persa: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    /**
     * Se il login e' riuscito (campo "loginUsername" nella risposta),
     * crea la SessionInfo e aggiunge il giocatore alla partita corrente.
     */
    private String handlePostLogin(String jsonRequest, String jsonResponse) {
        try {
            JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (!response.has("loginUsername")) return jsonResponse;

            String username = response.get("loginUsername").getAsString();

            JsonObject request = JsonParser.parseString(jsonRequest).getAsJsonObject();
            int udpPort = request.has("udpPort") ? request.get("udpPort").getAsInt() : 0;

            InetAddress clientAddress = socket.getInetAddress();
            SessionInfo session = new SessionInfo(username, socket, in, out, clientAddress, udpPort);

            String loginError = sessionManager.login(session);
            if (loginError != null) {
                JsonObject errorResp = new JsonObject();
                errorResp.addProperty("status", "error");
                errorResp.addProperty("code", 4);
                errorResp.addProperty("message", loginError);
                return errorResp.toString();
            }

            loggedInUsername = username;

            Game currentGame = gameManager.getCurrentGame();
            if (currentGame != null && currentGame.isActive()) {
                currentGame.addPlayer(username);
            }

            response.remove("loginUsername");
            return response.toString();

        } catch (Exception e) {
            return jsonResponse;
        }
    }

    private void handlePostLogout(String jsonRequest, String jsonResponse) {
        try {
            JsonObject request = JsonParser.parseString(jsonRequest).getAsJsonObject();
            if (!"logout".equals(request.get("operation").getAsString())) return;

            JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if ("ok".equals(response.get("status").getAsString())) {
                loggedInUsername = null;
            }
        } catch (Exception e) {
            // parsing fallito, ignora
        }
    }

    /**
     * Se l'utente ha cambiato username, aggiorna la sessione nel SessionManager.
     */
    private void handlePostUpdateCredentials(String jsonRequest, String jsonResponse) {
        try {
            JsonObject request = JsonParser.parseString(jsonRequest).getAsJsonObject();
            if (!"updateCredentials".equals(request.get("operation").getAsString())) return;

            JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (!"ok".equals(response.get("status").getAsString())) return;

            if (response.has("newUsername") && loggedInUsername != null) {
                String newUsername = response.get("newUsername").getAsString();
                SessionInfo oldSession = sessionManager.logout(loggedInUsername);
                if (oldSession != null) {
                    SessionInfo newSession = new SessionInfo(newUsername, socket, in, out,
                            oldSession.getUdpAddress(), oldSession.getUdpPort());
                    sessionManager.login(newSession);
                }
                loggedInUsername = newUsername;
            }
        } catch (Exception e) {
            // parsing fallito, ignora
        }
    }

    /** Legge un messaggio length-prefixed. Ritorna null se il client si disconnette. */
    private String readMessage() throws IOException {
        try {
            int length = in.readInt();
            if (length <= 0 || length > 1024 * 1024) {
                System.err.println("[Connection] Lunghezza messaggio invalida: " + length);
                return null;
            }
            byte[] data = new byte[length];
            in.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (EOFException e) {
            return null;
        }
    }

    /** Invia un messaggio length-prefixed. Sincronizzato per evitare scritture concorrenti. */
    private synchronized void sendMessage(String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    private void cleanup() {
        if (loggedInUsername != null) {
            sessionManager.logout(loggedInUsername);
            System.out.println("[Connection] Auto-logout per disconnessione: " + loggedInUsername);
            loggedInUsername = null;
        }
        try { socket.close(); } catch (IOException e) { }
    }
}
