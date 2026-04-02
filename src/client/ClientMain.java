package client;

import java.io.IOException;

/**
 * Punto di ingresso del client Connections.
 * Avvia 3 thread: CLI (main), NIO Selector (I/O TCP), UDP Listener (notifiche).
 */
public class ClientMain {

    private static volatile int udpPort = 0;

    public static void main(String[] args) {
        NIOHandler nioHandler = null;
        UDPListener udpListener = null;

        try {
            // leggo i parametri dal file di config
            ClientConfig config = new ClientConfig("client.properties");

            // avvio il listener UDP per le notifiche asincrone dal server
            udpListener = new UDPListener(config.getUdpPort());
            udpPort = udpListener.getPort();
            Thread udpThread = new Thread(udpListener, "UDP-Listener");
            udpThread.setDaemon(true);
            udpThread.start();

            // mi connetto al server via NIO (TCP non-blocking)
            nioHandler = new NIOHandler();
            nioHandler.connect(config.getServerHost(), config.getServerPort());

            // avvio la CLI, che blocca il main thread fino a quit
            CLIHandler cli = new CLIHandler(nioHandler);
            cli.start();

        } catch (IOException e) {
            System.err.println("[Client] Errore: " + e.getMessage());
        } finally {
            if (nioHandler != null) nioHandler.close();
            if (udpListener != null) udpListener.stop();
        }
    }

    public static int getUdpPort() { return udpPort; }
}
