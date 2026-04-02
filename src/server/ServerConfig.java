package server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Configurazione del server letta da server.properties. */
public class ServerConfig {

    private final int tcpPort;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final long gameDurationSeconds;
    private final long persistenceIntervalSeconds;
    private final String gameDataFile;
    private final String persistenceDirectory;

    public ServerConfig(String configFile) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            /*Reads a property list (key and element pairs) from the input byte stream. The input stream
               is in a simple line-oriented format as specified in load(Reader)
             */
            props.load(fis);
        }

        this.tcpPort = Integer.parseInt(props.getProperty("tcp.port", "12345"));
        this.corePoolSize = Integer.parseInt(props.getProperty("threadpool.core", "10"));
        this.maxPoolSize = Integer.parseInt(props.getProperty("threadpool.max", "30"));
        this.queueCapacity = Integer.parseInt(props.getProperty("threadpool.queue", "50"));
        this.gameDurationSeconds = Long.parseLong(props.getProperty("game.duration.seconds", "300"));
        this.persistenceIntervalSeconds = Long.parseLong(props.getProperty("persistence.interval.seconds", "30"));
        this.gameDataFile = props.getProperty("game.data.file", "Connections_Data.json");
        this.persistenceDirectory = props.getProperty("persistence.directory", "data");
    }

    public int getTcpPort() { return tcpPort; }
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getQueueCapacity() { return queueCapacity; }
    public long getGameDurationSeconds() { return gameDurationSeconds; }
    public long getGameDurationMillis() { return gameDurationSeconds * 1000; }
    public long getPersistenceIntervalSeconds() { return persistenceIntervalSeconds; }
    public String getGameDataFile() { return gameDataFile; }
    public String getPersistenceDirectory() { return persistenceDirectory; }

    @Override
    public String toString() {
        return "ServerConfig{port=" + tcpPort
                + ", pool=" + corePoolSize + "-" + maxPoolSize + " (queue=" + queueCapacity + ")"
                + ", gameDuration=" + gameDurationSeconds + "s"
                + ", gameDataFile='" + gameDataFile + "'}";
    }
}
