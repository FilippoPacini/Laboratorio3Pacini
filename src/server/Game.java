package server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rappresenta una singola partita di Connections con i suoi 4 gruppi tematici.
 * La mappa dei giocatori usa ConcurrentHashMap per aggiunta concorrente durante la partita.
 */
public class Game {

    private final int gameId;
    private final List<GameGroup> groups;
    private final List<String> shuffledWords;
    private final long startTimeMillis;
    private final long durationMillis;
    private volatile boolean active;
    private final ConcurrentHashMap<String, PlayerGameState> playerStates;

    public Game(int gameId, List<GameGroup> groups, long durationMillis) {
        this.gameId = gameId;
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        this.durationMillis = durationMillis;
        this.startTimeMillis = System.currentTimeMillis();
        this.active = true;
        this.playerStates = new ConcurrentHashMap<>();

        List<String> allWords = new ArrayList<>();
        for (GameGroup g : groups) {
            allWords.addAll(g.getWords());
        }
        Collections.shuffle(allWords);
        this.shuffledWords = Collections.unmodifiableList(allWords);
    }

    /** Aggiunge un giocatore. Se gia' presente (riconnessione), restituisce lo stato esistente. */
    public PlayerGameState addPlayer(String username) {
        return playerStates.computeIfAbsent(username, PlayerGameState::new);
    }

    /** Valuta una proposta di 4 parole. */
    public ProposalResult evaluateProposal(String username, Set<String> proposedWords) {
        PlayerGameState state = playerStates.get(username);
        if (state == null) {
            return new ProposalResult(ProposalResult.Code.ERROR, "Giocatore non partecipa alla partita", null);
        }

        synchronized (state) {
            if (state.isFinished()) {
                return new ProposalResult(ProposalResult.Code.ERROR, "Partita gia' terminata per questo giocatore", null);
            }
            if (!active) {
                return new ProposalResult(ProposalResult.Code.ERROR, "Partita terminata", null);
            }
            if (proposedWords.size() != 4) {
                return new ProposalResult(ProposalResult.Code.MALFORMED, "La proposta deve contenere esattamente 4 parole", null);
            }

            Set<String> allGameWords = new HashSet<>(shuffledWords);
            if (!allGameWords.containsAll(proposedWords)) {
                return new ProposalResult(ProposalResult.Code.MALFORMED, "Una o piu' parole non appartengono alla partita", null);
            }

            // Controlla che nessuna parola sia gia' stata assegnata
            for (GameGroup correctGroup : state.getCorrectGroups()) {
                for (String word : proposedWords) {
                    if (correctGroup.getWords().contains(word)) {
                        return new ProposalResult(ProposalResult.Code.MALFORMED,
                                "Una o piu' parole gia' assegnate a un gruppo corretto", null);
                    }
                }
            }

            // Cerca corrispondenza con un gruppo
            for (GameGroup group : groups) {
                if (group.matches(proposedWords)) {
                    if (state.getCorrectGroups().contains(group)) {
                        return new ProposalResult(ProposalResult.Code.MALFORMED, "Gruppo gia' indovinato", null);
                    }
                    state.addCorrectGroup(group);
                    return new ProposalResult(ProposalResult.Code.CORRECT, "Gruppo corretto: " + group.getTheme(), group);
                }
            }

            state.addWrongAttempt();
            return new ProposalResult(ProposalResult.Code.WRONG, "Proposta sbagliata", null);
        }
    }

    /** Termina la partita. Tutti i giocatori ancora in corso diventano TIME_UP. */
    public void endGame() {
        active = false;
        for (PlayerGameState state : playerStates.values()) {
            state.timeUp();
        }
    }

    public long getRemainingTimeMillis() {
        if (!active) return 0;
        long elapsed = System.currentTimeMillis() - startTimeMillis;
        return Math.max(0, durationMillis - elapsed);
    }

    public long getRemainingTimeSeconds() {
        return getRemainingTimeMillis() / 1000;
    }

    public int getParticipantCount() { return playerStates.size(); }

    public int getCompletedCount() {
        int count = 0;
        for (PlayerGameState s : playerStates.values()) {
            PlayerGameState.Status st = s.getStatus();
            if (st == PlayerGameState.Status.WON || st == PlayerGameState.Status.LOST
                    || st == PlayerGameState.Status.TIME_UP) {
                count++;
            }
        }
        return count;
    }

    public int getInProgressCount() {
        int count = 0;
        for (PlayerGameState s : playerStates.values()) {
            if (s.getStatus() == PlayerGameState.Status.IN_PROGRESS) count++;
        }
        return count;
    }

    public int getWinnerCount() {
        int count = 0;
        for (PlayerGameState s : playerStates.values()) {
            if (s.getStatus() == PlayerGameState.Status.WON) count++;
        }
        return count;
    }

    public double getAverageScore() {
        if (playerStates.isEmpty()) return 0;
        int total = 0;
        for (PlayerGameState s : playerStates.values()) {
            total += s.getScore();
        }
        return (double) total / playerStates.size();
    }

    /** Parole non ancora assegnate correttamente dal giocatore. */
    public List<String> getRemainingWords(String username) {
        PlayerGameState state = playerStates.get(username);
        if (state == null) return new ArrayList<>(shuffledWords);

        Set<String> guessedWords = new HashSet<>();
        for (GameGroup g : state.getCorrectGroups()) {
            guessedWords.addAll(g.getWords());
        }

        List<String> remaining = new ArrayList<>();
        for (String w : shuffledWords) {
            if (!guessedWords.contains(w)) remaining.add(w);
        }
        return remaining;
    }

    public int getGameId() { return gameId; }
    public List<GameGroup> getGroups() { return groups; }
    public List<String> getShuffledWords() { return shuffledWords; }
    public boolean isActive() { return active; }
    public long getStartTimeMillis() { return startTimeMillis; }
    public long getDurationMillis() { return durationMillis; }
    public PlayerGameState getPlayerState(String username) { return playerStates.get(username); }
    public Collection<PlayerGameState> getAllPlayerStates() { return playerStates.values(); }
    public Map<String, PlayerGameState> getPlayerStatesMap() { return Collections.unmodifiableMap(playerStates); }

    public static class ProposalResult {
        public enum Code { CORRECT, WRONG, MALFORMED, ERROR }

        private final Code code;
        private final String message;
        private final GameGroup matchedGroup;

        public ProposalResult(Code code, String message, GameGroup matchedGroup) {
            this.code = code;
            this.message = message;
            this.matchedGroup = matchedGroup;
        }

        public Code getCode() { return code; }
        public String getMessage() { return message; }
        public GameGroup getMatchedGroup() { return matchedGroup; }
    }
}
