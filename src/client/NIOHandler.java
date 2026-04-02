package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Connessione TCP col server via Java NIO non-blocking.
 * Il thread CLI mette i messaggi nella coda di scrittura e attende la risposta.
 * Il selector thread gestisce l'I/O effettivo.
 * Framing: 4 byte (int big-endian) di lunghezza + payload JSON.
 */
public class NIOHandler {

    private SocketChannel channel;
    private Selector selector;
    private Thread selectorThread;
    private volatile boolean running;

    private final LinkedBlockingQueue<byte[]> writeQueue;
    private final LinkedBlockingQueue<String> responseQueue;

    // Stato macchina per gestire partial reads
    private ByteBuffer readLengthBuffer;
    private ByteBuffer readDataBuffer;
    private boolean readingLength;

    public NIOHandler() {
        this.writeQueue = new LinkedBlockingQueue<>();
        this.responseQueue = new LinkedBlockingQueue<>();
        this.running = false;
    }

    public void connect(String host, int port) throws IOException {
        selector = Selector.open();
        channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(host, port));
        channel.register(selector, SelectionKey.OP_CONNECT);

        while (!channel.isConnected()) {
            selector.select(1000);
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                if (key.isConnectable()) channel.finishConnect();
            }
        }

        channel.register(selector, SelectionKey.OP_READ);
        readLengthBuffer = ByteBuffer.allocate(4);
        readDataBuffer = null;
        readingLength = true;

        running = true;
        selectorThread = new Thread(this::selectorLoop, "NIO-Selector");
        selectorThread.setDaemon(true);
        selectorThread.start();
    }

    //  gira su un thread separato e gestisce lettura/scrittura col server
    private void selectorLoop() {
        while (running) {
            try {
                if (!writeQueue.isEmpty()) {
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }

                int readyChannels = selector.select(100);
                if (readyChannels == 0) continue;

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) continue;
                    if (key.isReadable()) handleRead();
                    if (key.isValid() && key.isWritable()) handleWrite(key);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[NIO] Errore: " + e.getMessage());
                    running = false;
                }
            }
        }
    }

    /** Lettura a stato-macchina: prima 4 byte di lunghezza, poi il corpo del messaggio. */
    private void handleRead() throws IOException {
        if (readingLength) {
            int bytesRead = channel.read(readLengthBuffer);
            if (bytesRead == -1) { running = false; return; }

            if (!readLengthBuffer.hasRemaining()) {
                readLengthBuffer.flip();
                int length = readLengthBuffer.getInt();
                readLengthBuffer.clear();

                if (length <= 0 || length > 1024 * 1024) {
                    System.err.println("[NIO] Lunghezza messaggio invalida: " + length);
                    return;
                }

                readDataBuffer = ByteBuffer.allocate(length);
                readingLength = false;
            }
        }

        if (!readingLength && readDataBuffer != null) {
            int bytesRead = channel.read(readDataBuffer);
            if (bytesRead == -1) { running = false; return; }

            if (!readDataBuffer.hasRemaining()) {
                readDataBuffer.flip();
                String message = StandardCharsets.UTF_8.decode(readDataBuffer).toString();
                responseQueue.offer(message);
                readDataBuffer = null;
                readingLength = true;
            }
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        byte[] data = writeQueue.poll();
        if (data == null) {
            key.interestOps(SelectionKey.OP_READ);
            return;
        }

        ByteBuffer writeBuffer = ByteBuffer.allocate(4 + data.length);
        writeBuffer.putInt(data.length);
        writeBuffer.put(data);
        writeBuffer.flip();

        while (writeBuffer.hasRemaining()) {
            int written = channel.write(writeBuffer);
            if (written == 0) Thread.yield();
        }

        if (writeQueue.isEmpty()) key.interestOps(SelectionKey.OP_READ);
    }

    // metto il messaggio nella coda, sveglio il selector, e aspetto la risposta (max 30s)
    /** Invia un messaggio e attende la risposta (bloccante per il chiamante). */
    public String sendAndReceive(String jsonMessage) throws IOException {
        if (!running) throw new IOException("Connessione chiusa");

        writeQueue.offer(jsonMessage.getBytes(StandardCharsets.UTF_8));
        selector.wakeup();

        try {
            String response = responseQueue.poll(30, TimeUnit.SECONDS);
            if (response == null) throw new IOException("Timeout in attesa della risposta");
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Attesa interrotta");
        }
    }

    public void close() {
        running = false;
        try {
            if (selector != null) { selector.wakeup(); selector.close(); }
            if (channel != null) channel.close();
        } catch (IOException e) { }
        if (selectorThread != null) selectorThread.interrupt();
    }

    public boolean isConnected() {
        return running && channel != null && channel.isConnected();
    }
}
