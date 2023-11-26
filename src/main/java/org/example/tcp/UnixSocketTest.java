package org.example.tcp;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class UnixSocketTest {

    public static final Path SOCKET_ADDRES_PATH = Path.of("/Users/denisfedorovich/IdeaProjects/Lab-1-Sockets/socket");
    public static final UnixDomainSocketAddress SOCKET_ADDRESS = UnixDomainSocketAddress.of(SOCKET_ADDRES_PATH);

    static class UnixServer extends Thread {

        private final CountDownLatch latch;

        private final int packetSize;

        private final int requestsCount;

        private final AtomicLong timeSpentSum = new AtomicLong(0);
        private final AtomicLong packetsReceivedSum = new AtomicLong(0);
        private final AtomicLong bytesReceivedSum = new AtomicLong(0);
        private final AtomicLong firstPacketLatencySum = new AtomicLong(0);

        UnixServer(CountDownLatch latch, int packetSize, int requestsCount) {
            this.latch = latch;
            this.packetSize = packetSize;
            this.requestsCount = requestsCount;
        }

        @Override
        public void run() {
            try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                serverSocketChannel.bind(SOCKET_ADDRESS);

                for (int i = 0; i < requestsCount; i++) {
                    try {
                        SocketChannel socketChannel = serverSocketChannel.accept();
//                        socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 204800);
                        handleClient(socketChannel);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                printStatistics();
            } catch (IOException e) {
                e.printStackTrace();
            }
            latch.countDown();
        }

        private void handleClient(SocketChannel socketChannel) {
            long startTime = System.nanoTime();

            AtomicLong firstPacketLatency = new AtomicLong(0);
            AtomicLong bytesReadSum = new AtomicLong(0);
            AtomicLong receivedPackets = new AtomicLong(0);
            ByteBuffer buffer = ByteBuffer.allocate(packetSize);

            long spentTime = Utils.executeAndMeasureTime(() -> {
                try {
                    int bytesRead;
                    Long firstPacketReadTime = null;

                    while ((bytesRead = socketChannel.read(buffer)) != -1) {
                        if (firstPacketReadTime == null && bytesRead > 0) {
                            firstPacketReadTime = System.nanoTime();
                        }
                        bytesReadSum.addAndGet(bytesRead);
                        receivedPackets.incrementAndGet();
                        buffer.clear(); // Prepare the buffer for the next read
                    }

                    firstPacketLatency.set(firstPacketReadTime - startTime);

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        socketChannel.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            timeSpentSum.addAndGet(spentTime);
            packetsReceivedSum.addAndGet(receivedPackets.get());
            bytesReceivedSum.addAndGet(bytesReadSum.get());
            firstPacketLatencySum.addAndGet(firstPacketLatency.get());
        }

        private void printStatistics() {
            DecimalFormat decimalFormat = new DecimalFormat();
            decimalFormat.setGroupingUsed(false);
            decimalFormat.setMaximumFractionDigits(2);
            double timeSpentSeconds = (double) timeSpentSum.get() / (double) 1_000_000_000L;
            double packetsPerSecond = (double) packetsReceivedSum.get() / timeSpentSeconds;
            double bytesPerSeconds = (double) bytesReceivedSum.get() / timeSpentSeconds;
            double averageLatency = (double) firstPacketLatencySum.get() / (double) 1_000_000 / (double) requestsCount;
            System.out.println("Time spent: " + decimalFormat.format(timeSpentSeconds) + " s");
            System.out.println("Packets per second: " + decimalFormat.format(packetsPerSecond));
            System.out.println("Megabytes per second: " + decimalFormat.format(bytesPerSeconds / (double) 1_000_000L));
            System.out.println("Average first packet latency: " + decimalFormat.format(averageLatency) + " ms");
        }
    }

    static class UnixClient extends Thread {

        private final CountDownLatch latch;

        private final int packetSize;
        private final int packetAmount;
        private final int requestsCount;

        UnixClient(CountDownLatch latch, int packetSize, int packetAmount, int requestsCount) {
            this.latch = latch;
            this.packetSize = packetSize;
            this.packetAmount = packetAmount;
            this.requestsCount = requestsCount;
        }

        @Override
        public void run() {
            long connectionTimeSum = 0;
            for (int i = 0; i < requestsCount; i++) {
                connectionTimeSum += sendPacketsToServer(packetSize, packetAmount);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("Average socket connection time: " + (connectionTimeSum / requestsCount / (double) 1_000_000) + " ms");
            latch.countDown();
        }

        private long sendPacketsToServer(int packetSize, int packetAmount) {
            long connectionTime = 0;

            try (SocketChannel socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
//                socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 204800);
                connectionTime = Utils.executeAndMeasureTime(() -> {
                    try {
                        socketChannel.connect(SOCKET_ADDRESS);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                byte[] packet = new byte[packetSize];
                double writingTimeSum = 0;

                for (int i = 0; i < packetAmount; i++) {
                    ByteBuffer buffer = ByteBuffer.wrap(packet);
                    long beforeWriteTime = System.nanoTime();
                    socketChannel.write(buffer);
                    writingTimeSum += System.nanoTime() - beforeWriteTime;
                }

//                System.out.println("Average writing time: " + (writingTimeSum / (double) packetAmount / (double) 1_000_000) + " ms");

            } catch (IOException e) {
                e.printStackTrace();
            }

            return connectionTime;
        }
    }

    public static void makeTest(int packetSize, int packetAmount, int requestsCount) {
        try {
            Files.deleteIfExists(SOCKET_ADDRES_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("\n============================Test started=============================\n");

        System.out.println("Test parameters: \n" +
                "   Number of requests: " + requestsCount +
                "\n   Packets size/amount (per request): size: " + packetSize + ", amount: " + packetAmount);
        CountDownLatch latch = new CountDownLatch(2);

        UnixServer blockingServer = new UnixServer(latch, packetSize, requestsCount);
        blockingServer.start();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        UnixClient blockingClient = new UnixClient(latch, packetSize, packetAmount, requestsCount);
        blockingClient.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("\n=============================Test ended==============================\n");

        try {
            Files.deleteIfExists(SOCKET_ADDRES_PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {

        // ==============================MB/s tests=====================================
//        makeTest(4096, 5000, 1);
//        makeTest(4096, 100000, 1);
//        makeTest(4096, 2000000, 1);
//        makeTest(20480, 5000, 1);
//        makeTest(20480, 100000, 1);
//        makeTest(20480, 2000000, 1);
//        makeTest(102400, 5000, 1);
//        makeTest(102400, 100000, 1);
//        makeTest(4096, 5000, 1);
//        makeTest(4096, 100000, 1);

        // ========================Latency/connection tests==============================
        makeTest(20480, 10, 1000);
        makeTest(4096, 10, 1000);

    }
}
