package Lab1Sockets.http;

import Lab1Sockets.http.nonblocking.NonBlockingWRServer;

import java.util.Random;
import java.util.function.Function;

public class Main {

    public static class RandomResponseProvider implements Client.ResponseProvider {
        final int leftDataBound, rightDataBound;
        Random random = new Random();

        public RandomResponseProvider(int leftDataBound, int rightDataBound) {
            this.leftDataBound = leftDataBound;
            this.rightDataBound = rightDataBound;
        }

        @Override
        public Function<byte[], byte[]> getResponseGeneratorFunction() {
            return (arr1) -> arr1;
        }

        @Override
        public boolean checkResponseCorectness(byte[] sentData, byte[] receivedData) {
            return sentData.length == receivedData.length;
        }

        @Override
        public byte[] getRequestData() {
            byte[] requestData = new byte[random.nextInt(leftDataBound, rightDataBound)];
            random.nextBytes(requestData);
            return requestData;
        }
    }

    public static class CachedRandomResponseProvider implements Client.ResponseProvider {
        final int leftDataBound, rightDataBound;
        Random random = new Random();
        final byte[] arr;

        public CachedRandomResponseProvider(int leftDataBound, int rightDataBound) {
            this.leftDataBound = leftDataBound;
            this.rightDataBound = rightDataBound;
            arr = new byte[random.nextInt(leftDataBound, rightDataBound)];
            random.nextBytes(arr);
        }

        @Override
        public Function<byte[], byte[]> getResponseGeneratorFunction() {
            return (arr1) -> arr1;
        }

        @Override
        public boolean checkResponseCorectness(byte[] sentData, byte[] receivedData) {
            return sentData.length == receivedData.length;
        }

        @Override
        public byte[] getRequestData() {
            return arr;
        }
    }

    public static void main(String[] args) {
        int leftDataBound = 1999999, rightDataBound = 2000000;
        int port = 8103;

        long timeSum = 0;
        long requestsSum = 0;
        long bytesSum = 0;

        for (int i = 0; i < 4; i++) {
            Client client = new SocketClient(port, 10, new CachedRandomResponseProvider(leftDataBound, rightDataBound));

            Server server = new NonBlockingWRServer(port, client);

//            Server server = new BlockingSyncServer(port, client);
//            Server server = new BlockingAsyncServer(port, client, 10);

            server.start();
            client.startSendingWithStatisticsCollecting();
            client.waitForWorkFinish();
            server.stop();

            System.out.printf("""
                        Client:
                        Successfull requests: %d
                        Unsuccessful requests %d
                        Bytes sent: %d
                        Time spent for requests: %d
                        """,
                    client.successfulRequestsCount.get(), client.unsuccesfulRequestsCount.get(),
                    client.bytesSent.get(), client.timeSpentForSuccessfulRequestsNs.get());

            timeSum += client.timeSpentForSuccessfulRequestsNs.get();
            requestsSum += client.successfulRequestsCount.get();
            bytesSum += client.bytesSent.get();

            try {
                Thread.sleep(40000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        double timeInSeconds = (double) timeSum / (double) 1_000_000_000;
        double requestsPerSecond = (double) requestsSum / timeInSeconds;
        double bytesPerSecond = (double) bytesSum / timeInSeconds;

        System.out.printf("""
                        Test:
                        Requests per second: %.1f
                        Bytes per second:  %.1f
                        Data size (in bytes): [%d, %d]""",
                requestsPerSecond, bytesPerSecond, leftDataBound, rightDataBound);

    }
}