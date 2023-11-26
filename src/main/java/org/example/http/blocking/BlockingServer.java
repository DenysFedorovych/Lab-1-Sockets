package org.example.http.blocking;

import org.example.http.Server;
import org.example.http.Client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class BlockingServer extends Server {

    public abstract void handleConnection(Socket clientSocket);

    private ServerSocket serverSocket;

    public BlockingServer(int port, Client client) {
        super(port, client);
        this.workingThread = new Thread(() -> {
//            long sumTimeOfHandling = 0;
//            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket socket = serverSocket.accept();
//                    long curr = System.nanoTime();
                    handleConnection(socket);
//                    sumTimeOfHandling += System.nanoTime() - curr;
//                    i++;
                } catch (Exception ignore) {}
            }

//            System.out.println("Server: i = " + i + ", avgTime: " + (sumTimeOfHandling / i));
        });
    }

    @Override
    public void start() {
        try {
            this.serverSocket = new ServerSocket(port, 50, InetAddress.getLocalHost());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
