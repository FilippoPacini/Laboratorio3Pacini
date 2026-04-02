package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * Persistenza periodica su file JSON.
 * Salva utenti e stato del server ogni N secondi, a fine partita e allo shutdown.
 * Usa scrittura atomica (temp file + rename) per evitare corruzione.
 */
public class PersistenceManager {

    private final String dataDirectory;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;
    private final UserManager userManager;
    private volatile int nextGameIndex;

    private static final String USERS_FILE = "users.json";
    private static final String STATE_FILE = "server_state.json";

    public PersistenceManager(String dataDirectory, UserManager userManager,
                              long periodicIntervalSeconds) {
        this.dataDirectory = dataDirectory;
        this.userManager = userManager;
        this.nextGameIndex = 0;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        new File(dataDirectory).mkdirs();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PersistenceScheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::saveAll,
                periodicIntervalSeconds, periodicIntervalSeconds, TimeUnit.SECONDS);
    }

    /** Carica i dati persistiti. Ritorna l'indice della prossima partita da giocare. */
    public int loadPersistedData() {
        loadUsers();
        loadServerState();
        return nextGameIndex;
    }

    public synchronized void saveAll() {
        try {
            saveUsers();
            saveServerState();
        } catch (Exception e) {
            System.err.println("[Persistence] Errore salvataggio: " + e.getMessage());
        }
    }

    public void setNextGameIndex(int index) { this.nextGameIndex = index; }

    public void forceSave() { saveAll(); }

    private void saveUsers() {
        writeAtomically(USERS_FILE, gson.toJson(userManager.getUsersMap()));
    }

    private void loadUsers() {
        File file = new File(dataDirectory + File.separator + USERS_FILE);
        if (!file.exists()) return;

        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<ConcurrentHashMap<String, UserRecord>>(){}.getType();
            ConcurrentHashMap<String, UserRecord> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                userManager.loadUsers(loaded);
                System.out.println("[Persistence] Caricati " + loaded.size() + " utenti");
            }
        } catch (Exception e) {
            System.err.println("[Persistence] Errore caricamento utenti: " + e.getMessage());
        }
    }

    private void saveServerState() {
        ServerState state = new ServerState();
        state.nextGameIndex = this.nextGameIndex;
        writeAtomically(STATE_FILE, gson.toJson(state));
    }

    private void loadServerState() {
        File file = new File(dataDirectory + File.separator + STATE_FILE);
        if (!file.exists()) return;

        try (Reader reader = new FileReader(file)) {
            ServerState state = gson.fromJson(reader, ServerState.class);
            if (state != null) {
                this.nextGameIndex = state.nextGameIndex;
                System.out.println("[Persistence] Ripristinato stato: prossima partita index=" + nextGameIndex);
            }
        } catch (Exception e) {
            System.err.println("[Persistence] Errore caricamento stato: " + e.getMessage());
        }
    }

    /** Scrive su file temporaneo e poi rinomina (atomico). */
    private void writeAtomically(String fileName, String content) {
        Path targetPath = Paths.get(dataDirectory, fileName);
        Path tempPath = Paths.get(dataDirectory, fileName + ".tmp");

        try {
            Files.write(tempPath, content.getBytes("UTF-8"),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[Persistence] Errore scrittura " + fileName + ": " + e.getMessage());
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                System.err.println("[Persistence] Fallback fallito: " + e2.getMessage());
            }
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try { scheduler.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        forceSave();
    }

    private static class ServerState {
        int nextGameIndex;
    }
}
