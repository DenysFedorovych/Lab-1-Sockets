package org.example.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public abstract class Client {

    public interface ResponseProvider {

        public abstract Function<byte[], byte[]> getResponseGeneratorFunction();

        public abstract boolean checkResponseCorectness(byte[] sentData, byte[] receivedData);

        public abstract byte[] getRequestData();

    }

    public abstract int sendDataAndGetResponse(byte[] data);

    private final static int NUMBER_OF_REQUESTS = 200;

    protected final int port;
    protected InetAddress localhost;
    public ResponseProvider responseProvider;

    private final List<Thread> sendingThreads = new ArrayList<>();

    private final CountDownLatch awaitLatch;

    public final AtomicLong timeSpentForSuccessfulRequestsNs = new AtomicLong(0);
    public final AtomicLong bytesSent = new AtomicLong(0);
    public final AtomicInteger unsuccesfulRequestsCount = new AtomicInteger(0);
    public final AtomicLong timeSpentForUnsuccessfulRequestsNs = new AtomicLong(0);
    public final AtomicInteger successfulRequestsCount = new AtomicInteger(0);

    public Client(int port, int numberOfSendingThreads, ResponseProvider responseProvider) {
        this.port = port;
        this.responseProvider = responseProvider;
        this.awaitLatch = new CountDownLatch(numberOfSendingThreads);
        try {
            this.localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException ignore) {}
        populateSendingThreads(numberOfSendingThreads);
    }

    public void startSendingWithStatisticsCollecting() {
        sendingThreads.forEach(Thread::start);
    }

    public void stopSendingThreads() {
        sendingThreads.forEach(Thread::interrupt);
        sendingThreads.clear();
    }

    public void waitForWorkFinish() {
        try {
            this.awaitLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void populateSendingThreads(int numberOfThreads) {
        int requestsLimit = NUMBER_OF_REQUESTS / numberOfThreads;
        for (int i = 0; i < numberOfThreads; i++) {
            sendingThreads.add(new Thread(() -> {
                long timeSpentForSuccessfulRequestsNs = 0;
                long bytesSent = 0;
                int unsuccesfulRequestsCount = 0;
                int successfulRequestsCount = 0;
                long timeSpentForUnsuccessfulRequestsNs = 0;

                while (!Thread.currentThread().isInterrupted() && successfulRequestsCount < requestsLimit) {
                    byte[] data = responseProvider.getRequestData();

                    long currentTime = System.nanoTime();
                    int sentBytes = sendDataAndGetResponse(data);
                    long spentTime = System.nanoTime() - currentTime;

                    bytesSent += sentBytes;

                    if (sentBytes == 0) {
                        unsuccesfulRequestsCount++;
                        timeSpentForUnsuccessfulRequestsNs += spentTime;
                    } else {
                        successfulRequestsCount++;
                        timeSpentForSuccessfulRequestsNs += spentTime;
                    }
                }

                this.timeSpentForSuccessfulRequestsNs.addAndGet(timeSpentForSuccessfulRequestsNs);
                this.bytesSent.addAndGet(bytesSent);
                this.unsuccesfulRequestsCount.addAndGet(unsuccesfulRequestsCount);
                this.timeSpentForUnsuccessfulRequestsNs.addAndGet(timeSpentForUnsuccessfulRequestsNs);
                this.successfulRequestsCount.addAndGet(successfulRequestsCount);
                this.awaitLatch.countDown();
            }));
        }
    }
}
