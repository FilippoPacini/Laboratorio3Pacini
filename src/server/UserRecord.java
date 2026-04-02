package server;

/**
 * Utente registrato con statistiche personali. Persistito in JSON.
 * Accesso concorrente protetto da synchronized(this).
 */
public class UserRecord {

    private String username;
    private String password;
    private int totalScore;
    private int gamesPlayed;
    private int gamesWon;
    private int gamesLost;
    private int gamesNotFinished;
    private int currentStreak;
    private int maxStreak;
    private int perfectPuzzles;
    // Indici: 0-3 = vinto con N errori, 4 = perso, 5 = non finito
    private int[] mistakeHistogram;

    public UserRecord(String username, String password) {
        this.username = username;
        this.password = password;
        this.mistakeHistogram = new int[6];
    }

    /** Costruttore vuoto per deserializzazione Gson. */
    public UserRecord() {
        this.mistakeHistogram = new int[6];
    }

    /** Aggiorna le statistiche a fine partita. */
    public synchronized void updateStatsFromGame(PlayerGameState state) {
        gamesPlayed++;
        totalScore += state.getScore();

        PlayerGameState.Status status = state.getStatus();
        int wrongAttempts = state.getWrongAttempts();

        switch (status) {
            case WON:
                gamesWon++;
                currentStreak++;
                if (currentStreak > maxStreak) maxStreak = currentStreak;
                if (wrongAttempts == 0) perfectPuzzles++;
                if (wrongAttempts >= 0 && wrongAttempts <= 3) mistakeHistogram[wrongAttempts]++;
                break;
            case LOST:
                gamesLost++;
                currentStreak = 0;
                mistakeHistogram[4]++;
                break;
            case TIME_UP:
                gamesNotFinished++;
                currentStreak = 0;
                mistakeHistogram[5]++;
                break;
            default:
                break;
        }
    }

    public synchronized String getUsername() { return username; }
    public synchronized String getPassword() { return password; }
    public synchronized int getTotalScore() { return totalScore; }
    public synchronized int getGamesPlayed() { return gamesPlayed; }
    public synchronized int getGamesWon() { return gamesWon; }
    public synchronized int getGamesLost() { return gamesLost; }
    public synchronized int getGamesNotFinished() { return gamesNotFinished; }
    public synchronized int getCurrentStreak() { return currentStreak; }
    public synchronized int getMaxStreak() { return maxStreak; }
    public synchronized int getPerfectPuzzles() { return perfectPuzzles; }
    public synchronized int[] getMistakeHistogram() { return mistakeHistogram.clone(); }

    public synchronized double getWinRate() {
        return (gamesPlayed == 0) ? 0.0 : (double) gamesWon / gamesPlayed * 100.0;
    }

    public synchronized double getLossRate() {
        return (gamesPlayed == 0) ? 0.0 : (double) gamesLost / gamesPlayed * 100.0;
    }

    public synchronized void setUsername(String username) { this.username = username; }
    public synchronized void setPassword(String password) { this.password = password; }
    public synchronized boolean checkPassword(String pwd) { return this.password.equals(pwd); }
}
