package org.example.http.blocking;

import org.example.http.Client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockingAsyncServer extends BlockingServer {

    private final ExecutorService executorService;

    public BlockingAsyncServer(int port, Client client, int numberOfWorkingTreads) {
        super(port, client);
        executorService = Executors.newFixedThreadPool(numberOfWorkingTreads);
    }

    @Override
    public void handleConnection(Socket clientSocket) {
        executorService.submit(() -> {
            try (InputStream inputStream = clientSocket.getInputStream()) {
                OutputStream outputStream = clientSocket.getOutputStream();
                byte[] inputData = inputStream.readAllBytes();
                this.receivedBytes.addAndGet(inputData.length);
                byte[] response = responseGeneratorFunction.apply(inputData);
                outputStream.write(response);
                clientSocket.shutdownOutput();
//                this.sentBytes.addAndGet(response.length);
                clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void stop() {
        super.stop();
        executorService.shutdown();
    }
}
