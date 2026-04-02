package server;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * Gestisce il ciclo di vita delle partite: creazione, timer, fine, transizione.
 * Le partite finite vengono salvate in gameHistory per le query storiche.
 */
public class GameManager {

    private final GameDataReader gameDataReader;
    private final long gameDurationMillis;
    private final UserManager userManager;
    private final SessionManager sessionManager;
    private final RankingManager rankingManager;
    private final PersistenceManager persistenceManager;
    private final NotificationService notificationService;

    private final ScheduledExecutorService gameScheduler;

    private volatile Game currentGame;
    private final ConcurrentHashMap<Integer, Game> gameHistory;
    private int gameIndex;
    private ScheduledFuture<?> currentGameEndTask;
    private volatile boolean running;

    public GameManager(GameDataReader gameDataReader, long gameDurationMillis,
                       UserManager userManager, SessionManager sessionManager,
                       RankingManager rankingManager, PersistenceManager persistenceManager,
                       NotificationService notificationService) {
        this.gameDataReader = gameDataReader;
        this.gameDurationMillis = gameDurationMillis;
        this.userManager = userManager;
        this.sessionManager = sessionManager;
        this.rankingManager = rankingManager;
        this.persistenceManager = persistenceManager;
        this.notificationService = notificationService;
        this.gameHistory = new ConcurrentHashMap<>();
        this.gameIndex = 0;
        this.running = true;

        this.gameScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GameScheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /** Avvia il ciclo delle partite, saltando quelle gia' giocate (riavvio). */
    public void start(int startIndex) throws IOException {
        this.gameIndex = startIndex;

        if (startIndex > 0) {
            System.out.println("[GameManager] Ripristino: skip di " + startIndex + " partite");
            for (int i = 0; i < startIndex; i++) {
                if (gameDataReader.readNextGame() == null) {
                    System.err.println("[GameManager] File partite esaurito durante il ripristino");
                    running = false;
                    return;
                }
            }
        }

        startNextGame();
    }

    private synchronized void startNextGame() {
        if (!running) return;

        try {
            GameDataReader.GameData data = gameDataReader.readNextGame();
            if (data == null) {
                System.out.println("[GameManager] File partite esaurito.");
                running = false;
                currentGame = null;
                return;
            }

            Game newGame = new Game(data.getGameId(), data.getGroups(), gameDurationMillis);
            currentGame = newGame;
            gameIndex++;
            persistenceManager.setNextGameIndex(gameIndex);

            System.out.println("[GameManager] Nuova partita: gameId=" + data.getGameId()
                    + ", durata=" + (gameDurationMillis / 1000) + "s");

            // Aggiungi tutti i giocatori attualmente loggati
            for (SessionInfo session : sessionManager.getAllSessions()) {
                newGame.addPlayer(session.getUsername());
            }

            notificationService.notifyNewGame(newGame, sessionManager);

            currentGameEndTask = gameScheduler.schedule(
                    this::onGameTimeUp, gameDurationMillis, TimeUnit.MILLISECONDS);

        } catch (IOException e) {
            System.err.println("[GameManager] Errore lettura prossima partita: " + e.getMessage());
            running = false;
        }
    }

    private void onGameTimeUp() {
        Game game = currentGame;
        if (game == null || !game.isActive()) return;

        System.out.println("[GameManager] Partita " + game.getGameId() + " terminata (tempo scaduto)");

        game.endGame();
        updatePlayerStats(game);
        gameHistory.put(game.getGameId(), game);
        rankingManager.recalculate(userManager.getAllUsers());
        notificationService.notifyGameEnd(game, sessionManager);
        persistenceManager.forceSave();
        startNextGame();
    }

    private void updatePlayerStats(Game game) {
        for (PlayerGameState state : game.getAllPlayerStates()) {
            UserRecord user = userManager.getUser(state.getUsername());
            if (user != null) {
                user.updateStatsFromGame(state);
            }
        }
    }

    public Game getCurrentGame() { return currentGame; }

    public Game getGameFromHistory(int gameId) { return gameHistory.get(gameId); }

    /** Restituisce una partita per id. gameId == -1 indica la partita corrente. */
    public Game getGame(int gameId) {
        if (gameId == -1) return currentGame;
        Game current = currentGame;
        if (current != null && current.getGameId() == gameId) return current;
        return gameHistory.get(gameId);
    }

    public boolean isRunning() { return running; }

    public void shutdown() {
        running = false;
        if (currentGameEndTask != null) currentGameEndTask.cancel(false);
        gameScheduler.shutdown();

        Game game = currentGame;
        if (game != null && game.isActive()) {
            game.endGame();
            updatePlayerStats(game);
            gameHistory.put(game.getGameId(), game);
        }
    }
}
