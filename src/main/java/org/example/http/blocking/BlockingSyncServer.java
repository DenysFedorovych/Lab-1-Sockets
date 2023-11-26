package org.example.http.blocking;

import org.example.http.Client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class BlockingSyncServer extends BlockingServer {

    public BlockingSyncServer(int port, Client client) {
        super(port, client);
    }

    @Override
    public void handleConnection(Socket clientSocket) {
        try (InputStream inputStream = clientSocket.getInputStream()) {
            OutputStream outputStream = clientSocket.getOutputStream();
            byte[] inputData = inputStream.readAllBytes();
            this.receivedBytes.addAndGet(inputData.length);
            byte[] response = responseGeneratorFunction.apply(inputData);
            outputStream.write(response);
            clientSocket.shutdownOutput();
            this.sentBytes.addAndGet(response.length);
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
