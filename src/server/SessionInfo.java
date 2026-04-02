package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;

/** Dati di sessione di un utente loggato: riferimenti TCP e indirizzo UDP. */
public class SessionInfo {

    private final String username;
    private final Socket tcpSocket;
    private final DataInputStream tcpIn;
    private final DataOutputStream tcpOut;
    private final InetAddress udpAddress;
    private final int udpPort;

    public SessionInfo(String username, Socket tcpSocket,
                       DataInputStream tcpIn, DataOutputStream tcpOut,
                       InetAddress udpAddress, int udpPort) {
        this.username = username;
        this.tcpSocket = tcpSocket;
        this.tcpIn = tcpIn;
        this.tcpOut = tcpOut;
        this.udpAddress = udpAddress;
        this.udpPort = udpPort;
    }

    public String getUsername() { return username; }
    public Socket getTcpSocket() { return tcpSocket; }
    public DataInputStream getTcpIn() { return tcpIn; }
    public DataOutputStream getTcpOut() { return tcpOut; }
    public InetAddress getUdpAddress() { return udpAddress; }
    public int getUdpPort() { return udpPort; }
}
