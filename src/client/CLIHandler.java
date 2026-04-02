package client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Scanner;

/** Interfaccia a linea di comando del client. */
public class CLIHandler {

    private final NIOHandler nioHandler;
    private final Scanner scanner;
    private String currentUsername;
    private boolean running;

    public CLIHandler(NIOHandler nioHandler) {
        this.nioHandler = nioHandler;
        this.scanner = new Scanner(System.in);
        this.currentUsername = null;
        this.running = true;
    }

    public void start() {
        printWelcome();

        while (running && nioHandler.isConnected()) {
            System.out.print("> ");
            System.out.flush();

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            try {
                processCommand(line);
            } catch (IOException e) {
                System.out.println("[Errore] Connessione persa: " + e.getMessage());
                running = false;
            } catch (Exception e) {
                System.out.println("[Errore] " + e.getMessage());
            }
        }
    }

    private void processCommand(String line) throws IOException {
        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            case "register":           handleRegister(parts); break;
            case "login":              handleLogin(parts); break;
            case "logout":             handleLogout(); break;
            case "update-credentials": handleUpdateCredentials(); break;
            case "propose":            handlePropose(line); break;
            case "game-info":          handleGameInfo(parts); break;
            case "game-stats":         handleGameStats(parts); break;
            case "leaderboard":        handleLeaderboard(parts); break;
            case "my-stats":           handleMyStats(); break;
            case "help":               printHelp(); break;
            case "quit": case "exit":  handleQuit(); break;
            default:
                System.out.println("Comando sconosciuto. Digita 'help' per la lista dei comandi.");
        }
    }

    private void handleRegister(String[] parts) throws IOException {
        if (parts.length < 3) { System.out.println("Uso: register <username> <password>"); return; }

        JsonObject request = new JsonObject();
        request.addProperty("operation", "register");
        request.addProperty("name", parts[1]);
        request.addProperty("psw", parts[2]);

        displayResponse(nioHandler.sendAndReceive(request.toString()));
    }

    private void handleLogin(String[] parts) throws IOException {
        if (parts.length < 3) { System.out.println("Uso: login <username> <password>"); return; }
        if (currentUsername != null) {
            System.out.println("Sei gia' loggato come " + currentUsername + ". Fai logout prima.");
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("operation", "login");
        request.addProperty("username", parts[1]);
        request.addProperty("psw", parts[2]);
        request.addProperty("udpPort", ClientMain.getUdpPort());

        String response = nioHandler.sendAndReceive(request.toString());
        JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();

        if ("ok".equals(responseObj.get("status").getAsString())) {
            currentUsername = parts[1];
            System.out.println("Login riuscito! Benvenuto, " + currentUsername);
            displayGameInfoFromLogin(responseObj);
        } else {
            displayResponse(response);
        }
    }

    private void handleLogout() throws IOException {
        if (currentUsername == null) { System.out.println("Non sei loggato."); return; }

        JsonObject request = new JsonObject();
        request.addProperty("operation", "logout");

        String response = nioHandler.sendAndReceive(request.toString());
        JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();

        if ("ok".equals(responseObj.get("status").getAsString())) {
            System.out.println("Logout effettuato. Arrivederci, " + currentUsername + "!");
            currentUsername = null;
        } else {
            displayResponse(response);
        }
    }

    private void handleUpdateCredentials() throws IOException {
        System.out.print("Username attuale: ");
        String oldName = scanner.nextLine().trim();
        System.out.print("Password attuale: ");
        String oldPsw = scanner.nextLine().trim();
        System.out.print("Nuovo username (invio per non cambiare): ");
        String newName = scanner.nextLine().trim();
        System.out.print("Nuova password (invio per non cambiare): ");
        String newPsw = scanner.nextLine().trim();

        JsonObject request = new JsonObject();
        request.addProperty("operation", "updateCredentials");
        request.addProperty("oldName", oldName);
        request.addProperty("oldPsw", oldPsw);
        if (!newName.isEmpty()) request.addProperty("newName", newName);
        if (!newPsw.isEmpty()) request.addProperty("newPsw", newPsw);

        String response = nioHandler.sendAndReceive(request.toString());
        JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();

        if ("ok".equals(responseObj.get("status").getAsString())) {
            System.out.println("Credenziali aggiornate.");
            if (responseObj.has("newUsername")) {
                currentUsername = responseObj.get("newUsername").getAsString();
                System.out.println("Nuovo username: " + currentUsername);
            }
        } else {
            displayResponse(response);
        }
    }

    // parsa i 4 token dalla riga di propose, supportando parole composte tra parentesi
    // es: propose (DOROTHY GALE) UNICORN (ICE CUBE) PRIDE
    private java.util.List<String> parseProposalWords(String line) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        // rimuovo "propose " dall'inizio
        String rest = line.substring("propose".length()).trim();
        int i = 0;
        while (i < rest.length()) {
            if (rest.charAt(i) == '(') {
                int close = rest.indexOf(')', i);
                if (close == -1) close = rest.length();
                tokens.add(rest.substring(i + 1, close).trim().toUpperCase());
                i = close + 1;
            } else if (!Character.isWhitespace(rest.charAt(i))) {
                int end = i;
                while (end < rest.length() && !Character.isWhitespace(rest.charAt(end))) end++;
                tokens.add(rest.substring(i, end).toUpperCase());
                i = end;
            } else {
                i++;
            }
        }
        return tokens;
    }

    private void handlePropose(String line) throws IOException {
        if (currentUsername == null) { System.out.println("Devi essere loggato."); return; }

        java.util.List<String> proposedWords = parseProposalWords(line);
        if (proposedWords.size() != 4) {
            System.out.println("Uso: propose <w1> <w2> <w3> <w4>");
            System.out.println("Per parole composte usare le parentesi: propose (ICE CUBE) WORD2 WORD3 WORD4");
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("operation", "submitProposal");
        JsonArray words = new JsonArray();
        for (String w : proposedWords) words.add(w);
        request.add("words", words);

        String response = nioHandler.sendAndReceive(request.toString());
        JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();

        if ("ok".equals(responseObj.get("status").getAsString())) {
            String result = responseObj.get("result").getAsString();
            if ("correct".equals(result)) {
                System.out.println("CORRETTO! Tema: " + responseObj.get("theme").getAsString());
                if ("WON".equals(responseObj.get("playerStatus").getAsString())) {
                    System.out.println("Hai vinto la partita!");
                }
                if (responseObj.has("remainingWords")) {
                    System.out.print("Parole rimanenti: ");
                    JsonArray remaining = responseObj.getAsJsonArray("remainingWords");
                    StringBuilder sb = new StringBuilder();
                    for (JsonElement w : remaining) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(w.getAsString());
                    }
                    System.out.println(sb.length() > 0 ? sb.toString() : "(nessuna)");
                }
            } else {
                System.out.println("SBAGLIATO!");
            }
            System.out.println("Gruppi corretti: " + responseObj.get("correctGroups").getAsInt()
                    + " | Errori: " + responseObj.get("wrongAttempts").getAsInt()
                    + " | Punteggio: " + responseObj.get("score").getAsInt());
            if ("LOST".equals(responseObj.get("playerStatus").getAsString())) {
                System.out.println("Hai raggiunto 4 errori. Hai perso questa partita.");
            }
        } else {
            displayResponse(response);
        }
    }

    private void handleGameInfo(String[] parts) throws IOException {
        if (currentUsername == null) { System.out.println("Devi essere loggato."); return; }

        JsonObject request = new JsonObject();
        request.addProperty("operation", "requestGameInfo");
        int gameId = -1;
        if (parts.length >= 2 && !parts[1].equalsIgnoreCase("current")) {
            try { gameId = Integer.parseInt(parts[1]); }
            catch (NumberFormatException e) { System.out.println("Uso: game-info [gameId | current]"); return; }
        }
        request.addProperty("gameId", gameId);

        String response = nioHandler.sendAndReceive(request.toString());
        JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();

        if ("ok".equals(responseObj.get("status").getAsString())) {
            displayGameInfo(responseObj);
        } else {
            displayResponse(response);
        }
    }

    private void handleGameStats(String[] parts) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("operation", "requestGameStats");
        int gameId = -1;
        if (parts.length >= 2 && !parts[1].equalsIgnoreCase("current")) {
            try { gameId = Integer.parseInt(parts[1]); }
            catch (NumberFormatException e) { System.out.println("Uso: game-stats [gameId | current]"); return; }
        }
        request.addProperty("gameId", gameId);

        String response = nioHandler.sendAndReceive(request.toString());
        JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();

        if ("ok".equals(responseObj.get("status").getAsString())) {
            displayGameStats(responseObj);
        } else {
            displayResponse(response);
        }
    }

    private void handleLeaderboard(String[] parts) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("operation", "requestLeaderboard");

        if (parts.length >= 3 && parts[1].equalsIgnoreCase("top")) {
            try { request.addProperty("topPlayers", Integer.parseInt(parts[2])); }
            catch (NumberFormatException e) { System.out.println("Uso: leaderboard top <K>"); return; }
        } else if (parts.length >= 3 && parts[1].equalsIgnoreCase("player")) {
            request.addProperty("playerName", parts[2]);
        } else {
            request.addProperty("topPlayers", 0);
        }

        String response = nioHandler.sendAndReceive(request.toString());
        JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();

        if ("ok".equals(responseObj.get("status").getAsString())) {
            displayLeaderboard(responseObj);
        } else {
            displayResponse(response);
        }
    }

    private void handleMyStats() throws IOException {
        if (currentUsername == null) { System.out.println("Devi essere loggato."); return; }

        JsonObject request = new JsonObject();
        request.addProperty("operation", "requestPlayerStats");

        String response = nioHandler.sendAndReceive(request.toString());
        JsonObject responseObj = JsonParser.parseString(response).getAsJsonObject();

        if ("ok".equals(responseObj.get("status").getAsString())) {
            displayPlayerStats(responseObj);
        } else {
            displayResponse(response);
        }
    }

    private void handleQuit() throws IOException {
        if (currentUsername != null) handleLogout();
        running = false;
        System.out.println("Disconnessione...");
    }

    // --- Display ---

    private void displayGameInfoFromLogin(JsonObject response) {
        int gameId = response.has("gameId") ? response.get("gameId").getAsInt() : -1;
        if (gameId == -1) {
            System.out.println("Nessuna partita in corso. Attendi la prossima...");
            return;
        }

        System.out.println("\nPartita in corso: #" + gameId);
        if (response.has("remainingSeconds")) {
            System.out.println("Tempo rimanente: " + response.get("remainingSeconds").getAsLong() + "s");
        }
        if (response.has("words")) {
            System.out.println("Parole:");
            JsonArray words = response.getAsJsonArray("words");
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
        }

        int correct = response.has("correctGroups") ? response.get("correctGroups").getAsInt() : 0;
        int wrong = response.has("wrongAttempts") ? response.get("wrongAttempts").getAsInt() : 0;
        int score = response.has("score") ? response.get("score").getAsInt() : 0;
        System.out.println("Gruppi corretti: " + correct + " | Errori: " + wrong + " | Punteggio: " + score);

        if (response.has("correctGroupDetails")) {
            JsonArray groups = response.getAsJsonArray("correctGroupDetails");
            if (groups.size() > 0) {
                System.out.println("Gruppi gia' indovinati:");
                for (JsonElement g : groups) {
                    JsonObject group = g.getAsJsonObject();
                    System.out.print("  [" + group.get("theme").getAsString() + "] ");
                    JsonArray gWords = group.getAsJsonArray("words");
                    StringBuilder gsb = new StringBuilder();
                    for (JsonElement w : gWords) {
                        if (gsb.length() > 0) gsb.append(", ");
                        gsb.append(w.getAsString());
                    }
                    System.out.println(gsb.toString());
                }
            }
        }
        System.out.println();
    }

    private void displayGameInfo(JsonObject response) {
        int gameId = response.get("gameId").getAsInt();
        String gameStatus = response.get("gameStatus").getAsString();

        System.out.println("Partita #" + gameId + " (" + gameStatus + ")");

        if ("active".equals(gameStatus)) {
            System.out.println("Tempo rimanente: " + response.get("remainingSeconds").getAsLong() + "s");
            if (response.has("remainingWords")) {
                System.out.print("Parole rimanenti: ");
                JsonArray words = response.getAsJsonArray("remainingWords");
                StringBuilder sb = new StringBuilder();
                for (JsonElement w : words) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(w.getAsString());
                }
                System.out.println(sb.toString());
            }
            if (response.has("correctGroupDetails")) {
                JsonArray groups = response.getAsJsonArray("correctGroupDetails");
                if (groups.size() > 0) {
                    System.out.println("Gruppi indovinati:");
                    for (JsonElement g : groups) {
                        JsonObject group = g.getAsJsonObject();
                        System.out.println("  [" + group.get("theme").getAsString() + "]");
                    }
                }
            }
        } else {
            if (response.has("allGroups")) {
                System.out.println("Soluzione:");
                JsonArray groups = response.getAsJsonArray("allGroups");
                for (JsonElement g : groups) {
                    JsonObject group = g.getAsJsonObject();
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

        if (response.has("correctGroups")) {
            System.out.println("Gruppi corretti: " + response.get("correctGroups").getAsInt()
                    + " | Errori: " + response.get("wrongAttempts").getAsInt()
                    + " | Punteggio: " + response.get("score").getAsInt()
                    + " | Stato: " + response.get("playerStatus").getAsString());
        }
    }

    private void displayGameStats(JsonObject response) {
        int gameId = response.get("gameId").getAsInt();
        String gameStatus = response.get("gameStatus").getAsString();

        System.out.println("Statistiche Partita #" + gameId + " (" + gameStatus + ")");

        if ("active".equals(gameStatus)) {
            System.out.println("Tempo rimanente: " + response.get("remainingSeconds").getAsLong() + "s");
            System.out.println("Giocatori in corso: " + response.get("inProgress").getAsInt());
            System.out.println("Giocatori che hanno finito: " + response.get("completed").getAsInt());
            System.out.println("Vincitori: " + response.get("winners").getAsInt());
        } else {
            System.out.println("Partecipanti totali: " + response.get("participants").getAsInt());
            System.out.println("Giocatori che hanno finito: " + response.get("completed").getAsInt());
            System.out.println("Vincitori: " + response.get("winners").getAsInt());
            System.out.println("Punteggio medio: " + response.get("averageScore").getAsDouble());
        }
    }

    private void displayLeaderboard(JsonObject response) {
        if (response.has("position")) {
            System.out.println("Giocatore: " + response.get("playerName").getAsString());
            System.out.println("Posizione: #" + response.get("position").getAsInt());
            System.out.println("Punteggio: " + response.get("totalScore").getAsInt());
            return;
        }

        if (response.has("leaderboard")) {
            JsonArray leaderboard = response.getAsJsonArray("leaderboard");
            System.out.println("Classifica:");
            System.out.printf("  %-6s %-20s %s%n", "Pos", "Giocatore", "Punteggio");
            System.out.println("  " + "-".repeat(40));
            for (JsonElement entry : leaderboard) {
                JsonObject e = entry.getAsJsonObject();
                System.out.printf("  %-6d %-20s %d%n",
                        e.get("position").getAsInt(),
                        e.get("username").getAsString(),
                        e.get("totalScore").getAsInt());
            }
            if (leaderboard.size() == 0) System.out.println("  (classifica vuota)");
        }
    }

    private void displayPlayerStats(JsonObject response) {
        System.out.println("Statistiche di " + response.get("username").getAsString());
        System.out.println("  Puzzle completati: " + response.get("puzzlesCompleted").getAsInt());
        System.out.println("  Win Rate: " + response.get("winRate").getAsDouble() + "%");
        System.out.println("  Loss Rate: " + response.get("lossRate").getAsDouble() + "%");
        System.out.println("  Streak attuale: " + response.get("currentStreak").getAsInt());
        System.out.println("  Streak massima: " + response.get("maxStreak").getAsInt());
        System.out.println("  Puzzle perfetti: " + response.get("perfectPuzzles").getAsInt());
        System.out.println("  Punteggio totale: " + response.get("totalScore").getAsInt());

        if (response.has("mistakeHistogram")) {
            JsonObject hist = response.getAsJsonObject("mistakeHistogram");
            System.out.println("\n  Istogramma errori:");
            System.out.println("    0 errori (perfetto):  " + hist.get("0_mistakes").getAsInt());
            System.out.println("    1 errore:             " + hist.get("1_mistake").getAsInt());
            System.out.println("    2 errori:             " + hist.get("2_mistakes").getAsInt());
            System.out.println("    3 errori:             " + hist.get("3_mistakes").getAsInt());
            System.out.println("    4 errori (perso):     " + hist.get("4_mistakes_lost").getAsInt());
            System.out.println("    Non finito in tempo:  " + hist.get("not_finished").getAsInt());
        }
    }

    private void displayResponse(String json) {
        try {
            JsonObject response = JsonParser.parseString(json).getAsJsonObject();
            String status = response.get("status").getAsString();
            if ("error".equals(status)) {
                int code = response.has("code") ? response.get("code").getAsInt() : 0;
                String message = response.has("message") ? response.get("message").getAsString() : "Errore sconosciuto";
                System.out.println("[Errore " + code + "] " + message);
            } else {
                String message = response.has("message") ? response.get("message").getAsString() : "OK";
                System.out.println(message);
            }
        } catch (Exception e) {
            System.out.println(json);
        }
    }

    private void printWelcome() {
        System.out.println("CONNECTIONS - Collega le parole e vinci!");
        System.out.println("Digita 'help' per la lista dei comandi.\n");
    }

    private void printHelp() {
        System.out.println("\nComandi disponibili:");
        System.out.println("  Gestione utente:");
        System.out.println("    register <username> <password>     Registra un nuovo account");
        System.out.println("    login <username> <password>        Effettua il login");
        System.out.println("    logout                             Effettua il logout");
        System.out.println("    update-credentials                 Modifica username o password");
        System.out.println();
        System.out.println("  Partita (login):");
        System.out.println("    propose <w1> <w2> <w3> <w4>       Invia una proposta di 4 parole");
        System.out.println("      (parole composte tra parentesi, es: propose (ICE CUBE) W2 W3 W4)");
        System.out.println("    game-info [gameId | current]       Info sulla partita");
        System.out.println("    game-stats [gameId | current]      Statistiche della partita");
        System.out.println();
        System.out.println("  Classifica e statistiche (login):");
        System.out.println("    leaderboard all                    Classifica completa");
        System.out.println("    leaderboard top <K>                Top K giocatori");
        System.out.println("    leaderboard player <nome>          Posizione di un giocatore");
        System.out.println("    my-stats                           Le tue statistiche personali");
        System.out.println();
        System.out.println("    quit                               Esci dal gioco");
        System.out.println();
    }

    public boolean isRunning() { return running; }
}
