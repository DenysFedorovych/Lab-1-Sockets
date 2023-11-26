package Lab1Sockets.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class SocketClient extends Client{

    public SocketClient(int port, int numberOfSendingThreads, ResponseProvider responseProvider) {
        super(port, numberOfSendingThreads, responseProvider);
    }

    @Override
    public int sendDataAndGetResponse(byte[] data) {
        try (Socket socket = new Socket(localhost.getHostName(), port);
             InputStream inputStream = socket.getInputStream()) {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(data);
            socket.shutdownOutput();
            byte[] response = inputStream.readAllBytes();
            return responseProvider.checkResponseCorectness(data, response) ? data.length : 0;
        } catch (Exception e) {
            System.out.println("Not accepted");
            e.printStackTrace();
            return 0;
        }
    }
}
