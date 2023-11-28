package Lab2IPC;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

public class SocketsTest implements IPCChannelsTests.IPCChannel {

    private final boolean isServer;
    private final int port;

    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream input;
    private OutputStream output;

    public SocketsTest(int port, boolean isServer) {
        this.port = port;
        this.isServer = isServer;
    }

    public void init(int bufferSize) throws IOException {
        if (isServer) {
            serverSocket = new ServerSocket(port);
            socket = serverSocket.accept();
            socket.setReceiveBufferSize(bufferSize);
        } else {
            socket = new Socket(InetAddress.getLocalHost(), port);
            socket.setSendBufferSize(bufferSize);
        }
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    @Override
    public void write(byte[] data) {
        try {
            output.write(data);
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException("Error writing data through socket", e);
        }
    }

    @Override
    public void read(byte[] buffer) {
        try {
            int bytesRead = input.read(buffer);
            if (bytesRead == -1) {
                throw new IOException("Socket connection closed");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading data from socket", e);
        }
    }

    @Override
    public void close() {
        try {
            input.close();
            output.close();
            socket.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void clear() {
        // No need to implement clearing for socket IPC
    }

    @Override
    public long getMaxCapacity() {
        // Capacity for sockets is not applicable
        return 0;
    }

    public static void main(String[] args) {
        int port = 12345;
        int bufferSize = 102400 * 2000;

        SocketsTest serverChannel = new SocketsTest(port, true);
        SocketsTest clientChannel = new SocketsTest(port, false);

        CountDownLatch latch = new CountDownLatch(2);

        new Thread(() -> {
            try {
                serverChannel.init(bufferSize);
                latch.countDown();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        new Thread(() -> {
            try {
                clientChannel.init(bufferSize);
                latch.countDown();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Test your IPC channel using IPCChannelsTests
        IPCChannelsTests ipcTests = new IPCChannelsTests(serverChannel, clientChannel);
        ipcTests.makeTest();
    }
}

