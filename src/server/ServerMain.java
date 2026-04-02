package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

/** Punto di ingresso del server Connections. */
public class ServerMain {

    public static void main(String[] args) {
        try {
            // carico i parametri dal file di config
            ServerConfig config = new ServerConfig("server.properties");
            System.out.println("[Server] " + config);

            // inizializzo i manager principali
            UserManager userManager = new UserManager();
            SessionManager sessionManager = new SessionManager();
            RankingManager rankingManager = new RankingManager();

            // persistenza: carica utenti e stato dal disco (se ci sono da un avvio precedente)
            PersistenceManager persistenceManager = new PersistenceManager(
                    config.getPersistenceDirectory(), userManager, config.getPersistenceIntervalSeconds());
            int nextGameIndex = persistenceManager.loadPersistedData();

            // ricalcolo la classifica con gli utenti appena caricati
            rankingManager.recalculate(userManager.getAllUsers());

            NotificationService notificationService = new NotificationService();
            GameDataReader gameDataReader = new GameDataReader(config.getGameDataFile());

            // il GameManager gestisce il ciclo delle partite (creazione, timer, fine, nuova)
            GameManager gameManager = new GameManager(
                    gameDataReader, config.getGameDurationMillis(),
                    userManager, sessionManager, rankingManager,
                    persistenceManager, notificationService);

            // il RequestHandler smista e gestisce tutti i comandi JSON dei client
            RequestHandler requestHandler = new RequestHandler(
                    userManager, sessionManager, gameManager, rankingManager);

            // creo il pool di thread per gestire le connessioni in parallelo
            // CallerRunsPolicy: se il pool e' pieno, il thread di accept esegue il task
            ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                    config.getCorePoolSize(), config.getMaxPoolSize(),
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(config.getQueueCapacity()),
                    new ThreadPoolExecutor.CallerRunsPolicy());

            // avvio la prima partita (o riprendo da dove ero rimasto)
            gameManager.start(nextGameIndex);

            // alla chiusura del server, salvo tutto e fermo i thread
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Server] Shutdown...");
                gameManager.shutdown();
                persistenceManager.shutdown();
                notificationService.close();
                threadPool.shutdown();
                try { threadPool.awaitTermination(10, TimeUnit.SECONDS); }
                catch (InterruptedException e) { threadPool.shutdownNow(); }
                try { gameDataReader.close(); } catch (IOException e) { }
            }));

            // loop principale: accetto connessioni e le passo al pool
            System.out.println("[Server] In ascolto su porta " + config.getTcpPort());
            try (ServerSocket serverSocket = new ServerSocket(config.getTcpPort())) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(new ConnectionHandler(
                            clientSocket, requestHandler, sessionManager, gameManager));
                }
            }

        } catch (IOException e) {
            System.err.println("[Server] Errore fatale: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
