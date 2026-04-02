package server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Dispatcher e logica di tutti i comandi ricevuti dai client.
 * Fa switch su "operation" nel JSON e delega al metodo appropriato.
 *
 * Codici di errore: 1=nome gia' registrato, 2=password errata, 3=utente non trovato,
 * 4=gia' loggato, 5=non loggato, 6=proposta malformata, 7=partita non trovata,
 * 8=partita terminata, 9=max errori, 10=generico.
 */
public class RequestHandler {

    private final UserManager userManager;
    private final SessionManager sessionManager;
    private final GameManager gameManager;
    private final RankingManager rankingManager;

    public RequestHandler(UserManager userManager, SessionManager sessionManager,
                          GameManager gameManager, RankingManager rankingManager) {
        this.userManager = userManager;
        this.sessionManager = sessionManager;
        this.gameManager = gameManager;
        this.rankingManager = rankingManager;
    }

    public String handle(String jsonRequest, String currentUsername, SessionInfo session) {
        try {
            JsonObject request = JsonParser.parseString(jsonRequest).getAsJsonObject();
            String operation = request.get("operation").getAsString();

            switch (operation) {
                case "register":           return handleRegister(request);
                case "updateCredentials":  return handleUpdateCredentials(request);
                case "login":              return handleLogin(request, session);
                case "logout":             return handleLogout(currentUsername);
                case "submitProposal":     return handleSubmitProposal(request, currentUsername);
                case "requestGameInfo":    return handleRequestGameInfo(request, currentUsername);
                case "requestGameStats":   return handleRequestGameStats(request, currentUsername);
                case "requestLeaderboard": return handleRequestLeaderboard(request, currentUsername);
                case "requestPlayerStats": return handleRequestPlayerStats(currentUsername);
                default: return errorResponse(10, "Operazione sconosciuta: " + operation);
            }
        } catch (Exception e) {
            return errorResponse(10, "Errore nel parsing della richiesta: " + e.getMessage());
        }
    }

    private String handleRegister(JsonObject request) {
        String name = request.get("name").getAsString();
        String psw = request.get("psw").getAsString();

        String error = userManager.register(name, psw);
        if (error != null) return errorResponse(1, error);

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("message", "Registrazione completata");
        return response.toString();
    }

    private String handleUpdateCredentials(JsonObject request) {
        String oldName = request.get("oldName").getAsString();
        String oldPsw = request.get("oldPsw").getAsString();
        String newName = request.has("newName") && !request.get("newName").isJsonNull()
                ? request.get("newName").getAsString() : null;
        String newPsw = request.has("newPsw") && !request.get("newPsw").isJsonNull()
                ? request.get("newPsw").getAsString() : null;

        String error = userManager.updateCredentials(oldName, newName, oldPsw, newPsw);
        if (error != null) {
            int code = error.contains("Password") ? 2 : error.contains("non trovato") ? 3 : 1;
            return errorResponse(code, error);
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("message", "Credenziali aggiornate");
        if (newName != null && !newName.isEmpty()) {
            response.addProperty("newUsername", newName);
        }
        return response.toString();
    }

    /**
     * Il login restituisce un campo "loginUsername" usato internamente dal ConnectionHandler
     * per creare la sessione. Viene rimosso prima di inviare la risposta al client.
     */
    private String handleLogin(JsonObject request, SessionInfo existingSession) {
        String username = request.get("username").getAsString();
        String psw = request.get("psw").getAsString();

        UserRecord user = userManager.authenticate(username, psw);
        if (user == null) return errorResponse(2, "Credenziali errate");
        if (sessionManager.isLoggedIn(username)) return errorResponse(4, "Utente gia' loggato");

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("message", "Login riuscito");
        response.addProperty("loginUsername", username);

        Game currentGame = gameManager.getCurrentGame();
        if (currentGame != null && currentGame.isActive()) {
            response.addProperty("gameId", currentGame.getGameId());
            response.addProperty("remainingSeconds", currentGame.getRemainingTimeSeconds());

            JsonArray wordsArray = new JsonArray();
            PlayerGameState existingState = currentGame.getPlayerState(username);

            if (existingState != null) {
                // Riconnessione: invia stato attuale
                for (String word : currentGame.getRemainingWords(username)) wordsArray.add(word);
                response.add("words", wordsArray);
                response.addProperty("correctGroups", existingState.getCorrectCount());
                response.addProperty("wrongAttempts", existingState.getWrongAttempts());
                response.addProperty("score", existingState.getScore());
                response.addProperty("playerStatus", existingState.getStatus().name());

                JsonArray correctGroupsArray = new JsonArray();
                for (GameGroup g : existingState.getCorrectGroups()) {
                    JsonObject groupObj = new JsonObject();
                    groupObj.addProperty("theme", g.getTheme());
                    JsonArray gWords = new JsonArray();
                    for (String w : g.getWords()) gWords.add(w);
                    groupObj.add("words", gWords);
                    correctGroupsArray.add(groupObj);
                }
                response.add("correctGroupDetails", correctGroupsArray);
            } else {
                // Prima connessione
                for (String word : currentGame.getShuffledWords()) wordsArray.add(word);
                response.add("words", wordsArray);
                response.addProperty("correctGroups", 0);
                response.addProperty("wrongAttempts", 0);
                response.addProperty("score", 0);
                response.addProperty("playerStatus", "IN_PROGRESS");
            }
        } else {
            response.addProperty("gameId", -1);
            response.addProperty("message", "Nessuna partita in corso");
        }

        return response.toString();
    }

    private String handleLogout(String currentUsername) {
        if (currentUsername == null) return errorResponse(5, "Non sei loggato");
        sessionManager.logout(currentUsername);

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("message", "Logout effettuato");
        return response.toString();
    }

    private String handleSubmitProposal(JsonObject request, String currentUsername) {
        if (currentUsername == null) return errorResponse(5, "Non sei loggato");

        Game currentGame = gameManager.getCurrentGame();
        if (currentGame == null || !currentGame.isActive()) return errorResponse(7, "Nessuna partita in corso");

        JsonArray wordsArray = request.getAsJsonArray("words");
        Set<String> proposedWords = new HashSet<>();
        for (int i = 0; i < wordsArray.size(); i++) {
            proposedWords.add(wordsArray.get(i).getAsString().toUpperCase());
        }

        Game.ProposalResult result = currentGame.evaluateProposal(currentUsername, proposedWords);
        JsonObject response = new JsonObject();
        PlayerGameState state = currentGame.getPlayerState(currentUsername);

        switch (result.getCode()) {
            case CORRECT:
                response.addProperty("status", "ok");
                response.addProperty("result", "correct");
                response.addProperty("theme", result.getMatchedGroup().getTheme());
                response.addProperty("correctGroups", state.getCorrectCount());
                response.addProperty("wrongAttempts", state.getWrongAttempts());
                response.addProperty("score", state.getScore());
                response.addProperty("playerStatus", state.getStatus().name());
                JsonArray remaining = new JsonArray();
                for (String w : currentGame.getRemainingWords(currentUsername)) remaining.add(w);
                response.add("remainingWords", remaining);
                break;

            case WRONG:
                response.addProperty("status", "ok");
                response.addProperty("result", "wrong");
                response.addProperty("correctGroups", state.getCorrectCount());
                response.addProperty("wrongAttempts", state.getWrongAttempts());
                response.addProperty("score", state.getScore());
                response.addProperty("playerStatus", state.getStatus().name());
                break;

            case MALFORMED:
                response.addProperty("status", "error");
                response.addProperty("code", 6);
                response.addProperty("message", result.getMessage());
                break;

            case ERROR:
                response.addProperty("status", "error");
                response.addProperty("code", 8);
                response.addProperty("message", result.getMessage());
                break;
        }

        return response.toString();
    }

    private String handleRequestGameInfo(JsonObject request, String currentUsername) {
        if (currentUsername == null) return errorResponse(5, "Non sei loggato");

        int gameId = request.get("gameId").getAsInt();
        Game game = gameManager.getGame(gameId);
        if (game == null) return errorResponse(7, "Partita non trovata");

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("gameId", game.getGameId());

        PlayerGameState state = game.getPlayerState(currentUsername);

        if (game.isActive()) {
            response.addProperty("gameStatus", "active");
            response.addProperty("remainingSeconds", game.getRemainingTimeSeconds());

            if (state != null) {
                JsonArray remainingWords = new JsonArray();
                for (String w : game.getRemainingWords(currentUsername)) remainingWords.add(w);
                response.add("remainingWords", remainingWords);

                JsonArray correctGroupsArray = new JsonArray();
                for (GameGroup g : state.getCorrectGroups()) {
                    JsonObject groupObj = new JsonObject();
                    groupObj.addProperty("theme", g.getTheme());
                    JsonArray gWords = new JsonArray();
                    for (String w : g.getWords()) gWords.add(w);
                    groupObj.add("words", gWords);
                    correctGroupsArray.add(groupObj);
                }
                response.add("correctGroupDetails", correctGroupsArray);
                response.addProperty("correctGroups", state.getCorrectCount());
                response.addProperty("wrongAttempts", state.getWrongAttempts());
                response.addProperty("score", state.getScore());
                response.addProperty("playerStatus", state.getStatus().name());
            } else {
                response.addProperty("correctGroups", 0);
                response.addProperty("wrongAttempts", 0);
                response.addProperty("score", 0);
                response.addProperty("playerStatus", "NOT_PARTICIPANT");
            }
        } else {
            response.addProperty("gameStatus", "finished");

            JsonArray allGroups = new JsonArray();
            for (GameGroup g : game.getGroups()) {
                JsonObject groupObj = new JsonObject();
                groupObj.addProperty("theme", g.getTheme());
                JsonArray gWords = new JsonArray();
                for (String w : g.getWords()) gWords.add(w);
                groupObj.add("words", gWords);
                allGroups.add(groupObj);
            }
            response.add("allGroups", allGroups);

            if (state != null) {
                response.addProperty("correctGroups", state.getCorrectCount());
                response.addProperty("wrongAttempts", state.getWrongAttempts());
                response.addProperty("score", state.getScore());
                response.addProperty("playerStatus", state.getStatus().name());
            }
        }

        return response.toString();
    }

    private String handleRequestGameStats(JsonObject request, String currentUsername) {
        if (currentUsername == null) return errorResponse(5, "Non sei loggato");

        int gameId = request.get("gameId").getAsInt();
        Game game = gameManager.getGame(gameId);
        if (game == null) return errorResponse(7, "Partita non trovata");

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("gameId", game.getGameId());

        if (game.isActive()) {
            response.addProperty("gameStatus", "active");
            response.addProperty("remainingSeconds", game.getRemainingTimeSeconds());
            response.addProperty("inProgress", game.getInProgressCount());
            response.addProperty("completed", game.getCompletedCount());
            response.addProperty("winners", game.getWinnerCount());
        } else {
            response.addProperty("gameStatus", "finished");
            response.addProperty("participants", game.getParticipantCount());
            response.addProperty("completed", game.getCompletedCount());
            response.addProperty("winners", game.getWinnerCount());
            response.addProperty("averageScore", Math.round(game.getAverageScore() * 100.0) / 100.0);
        }

        return response.toString();
    }

    private String handleRequestLeaderboard(JsonObject request, String currentUsername) {
        if (currentUsername == null) return errorResponse(5, "Non sei loggato");

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");

        if (request.has("playerName") && !request.get("playerName").isJsonNull()) {
            String playerName = request.get("playerName").getAsString();
            int position = rankingManager.getPosition(playerName);
            if (position == -1) return errorResponse(3, "Giocatore non trovato in classifica");

            RankingManager.RankingEntry entry = rankingManager.getPlayerRanking(playerName);
            response.addProperty("playerName", playerName);
            response.addProperty("position", position);
            response.addProperty("totalScore", entry != null ? entry.getTotalScore() : 0);
            return response.toString();
        }

        List<RankingManager.RankingEntry> ranking;
        if (request.has("topPlayers") && !request.get("topPlayers").isJsonNull()) {
            int topK = request.get("topPlayers").getAsInt();
            ranking = (topK <= 0) ? rankingManager.getFullRanking() : rankingManager.getTopK(topK);
        } else {
            ranking = rankingManager.getFullRanking();
        }

        JsonArray rankingArray = new JsonArray();
        int pos = 1;
        for (RankingManager.RankingEntry entry : ranking) {
            JsonObject entryObj = new JsonObject();
            entryObj.addProperty("position", pos++);
            entryObj.addProperty("username", entry.getUsername());
            entryObj.addProperty("totalScore", entry.getTotalScore());
            rankingArray.add(entryObj);
        }
        response.add("leaderboard", rankingArray);

        return response.toString();
    }

    private String handleRequestPlayerStats(String currentUsername) {
        if (currentUsername == null) return errorResponse(5, "Non sei loggato");

        UserRecord user = userManager.getUser(currentUsername);
        if (user == null) return errorResponse(3, "Utente non trovato");

        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("username", currentUsername);
        response.addProperty("puzzlesCompleted", user.getGamesPlayed());
        response.addProperty("winRate", Math.round(user.getWinRate() * 100.0) / 100.0);
        response.addProperty("lossRate", Math.round(user.getLossRate() * 100.0) / 100.0);
        response.addProperty("currentStreak", user.getCurrentStreak());
        response.addProperty("maxStreak", user.getMaxStreak());
        response.addProperty("perfectPuzzles", user.getPerfectPuzzles());
        response.addProperty("totalScore", user.getTotalScore());

        int[] histogram = user.getMistakeHistogram();
        JsonObject histObj = new JsonObject();
        histObj.addProperty("0_mistakes", histogram[0]);
        histObj.addProperty("1_mistake", histogram[1]);
        histObj.addProperty("2_mistakes", histogram[2]);
        histObj.addProperty("3_mistakes", histogram[3]);
        histObj.addProperty("4_mistakes_lost", histogram[4]);
        histObj.addProperty("not_finished", histogram[5]);
        response.add("mistakeHistogram", histObj);

        return response.toString();
    }

    private String errorResponse(int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("status", "error");
        response.addProperty("code", code);
        response.addProperty("message", message);
        return response.toString();
    }
}
