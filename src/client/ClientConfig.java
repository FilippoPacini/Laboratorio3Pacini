package client;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/** Configurazione del client letta da client.properties. */
public class ClientConfig {

    private final String serverHost;
    private final int serverPort;
    private final int udpPort;

    public ClientConfig(String configFile) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
        }
        this.serverHost = props.getProperty("server.host", "localhost");
        this.serverPort = Integer.parseInt(props.getProperty("server.port", "12345"));
        this.udpPort = Integer.parseInt(props.getProperty("udp.port", "12346"));
    }

    public String getServerHost() { return serverHost; }
    public int getServerPort() { return serverPort; }
    public int getUdpPort() { return udpPort; }
}
