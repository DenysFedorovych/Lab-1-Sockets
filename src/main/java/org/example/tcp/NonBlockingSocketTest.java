package org.example.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class NonBlockingSocketTest {

    public static final InetSocketAddress LOCALHOST = new InetSocketAddress("localhost", 8097);

    static class NonBlockingServer extends Thread {
        private final int requestsCount;
        private final int packetSize;
        private final CountDownLatch latch;

        private final AtomicLong timeSpentSum = new AtomicLong(0);
        private final AtomicLong packetsReceivedSum = new AtomicLong(0);
        private final AtomicLong bytesReceivedSum = new AtomicLong(0);
        private final AtomicLong firstPacketLatencySum = new AtomicLong(0);

        public NonBlockingServer(int requestsCount, int packetSize, CountDownLatch latch) {
            this.requestsCount = requestsCount;
            this.packetSize = packetSize;
            this.latch = latch;
        }

        @Override
        public void run() {
            try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
                 Selector selector = Selector.open()) {
                serverSocketChannel.socket().setReuseAddress(true);
                serverSocketChannel.bind(LOCALHOST);
                serverSocketChannel.configureBlocking(false);


                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

                for (int i = 0; i < requestsCount; i++) {
                    long startTime = System.nanoTime();

                    while (System.nanoTime() - startTime < 1_000_000_000L) {
                        if (selector.select(1) > 0) {
                            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                            while (keyIterator.hasNext()) {
                                SelectionKey key = keyIterator.next();
                                keyIterator.remove();

                                if (key.isAcceptable()) {
                                    SocketChannel socketChannel = serverSocketChannel.accept();
                                    socketChannel.configureBlocking(false);
                                    socketChannel.register(selector, SelectionKey.OP_READ);
                                } else if (key.isReadable()) {
                                    handleClient((SocketChannel) key.channel());
                                    key.cancel();
                                }
                            }
                        }
                    }
                }
                serverSocketChannel.socket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            printStatistics();
            latch.countDown();
        }

        private void handleClient(SocketChannel socketChannel) throws IOException {
            long startTime = System.nanoTime();

            AtomicLong firstPacketLatency = new AtomicLong(0);
            AtomicLong bytesReadSum = new AtomicLong(0);
            AtomicLong receivedPackets = new AtomicLong(0);
            ByteBuffer buffer = ByteBuffer.allocate(packetSize);

            int bytesRead = 0;
            Long firstPacketReadTime = null;

            while (bytesRead != -1) {
                bytesRead = socketChannel.read(buffer);
                if (firstPacketReadTime == null && bytesRead > 0) {
                    firstPacketReadTime = System.nanoTime();
                }
                bytesReadSum.addAndGet(bytesRead);
                receivedPackets.incrementAndGet();
                buffer.clear();
            }

            firstPacketLatency.set(firstPacketReadTime - startTime);

            timeSpentSum.addAndGet(System.nanoTime() - startTime);
            packetsReceivedSum.addAndGet(receivedPackets.get());
            bytesReceivedSum.addAndGet(bytesReadSum.get());
            firstPacketLatencySum.addAndGet(firstPacketLatency.get());

            socketChannel.close();
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

     static class NonBlockingClient extends Thread {

            private final CountDownLatch latch;
            private final int packetSize;
            private final int packetAmount;
            private final int requestsCount;

            public NonBlockingClient(CountDownLatch latch, int packetSize, int packetAmount, int requestsCount) {
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

                try (SocketChannel socketChannel = SocketChannel.open()) {
                    socketChannel.configureBlocking(false);
                    socketChannel.connect(LOCALHOST);

                    Selector selector = Selector.open();
                    socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE);

                    long startTime = System.nanoTime();

                    while (selector.select(1) > 0) {
                        Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();
                            keyIterator.remove();

                            if (key.isConnectable()) {
                                SocketChannel channel = (SocketChannel) key.channel();

                                if (channel.finishConnect()) {
                                    key.interestOps(SelectionKey.OP_WRITE);
                                }
                            } else if (key.isWritable()) {
                                SocketChannel channel = (SocketChannel) key.channel();
                                connectionTime = System.nanoTime() - startTime;

                                byte[] packet = new byte[packetSize];


                                double writingTimeSum = 0;

                                for (int i = 0; i < packetAmount; i++) {
                                    ByteBuffer buffer = ByteBuffer.wrap(packet);
                                    long beforeWriteTime = System.nanoTime();
                                    channel.write(buffer);
                                    long writeTime = System.nanoTime() - beforeWriteTime;
                                    writingTimeSum += writeTime;
                                    if (writeTime > 100_000) {
                                        System.out.println("Big write time: " + writeTime);
                                    }
                                }

                                System.out.println("Average writing time: " + (writingTimeSum / (double) packetAmount / (double) 1_000_000) + " ms");

                                key.interestOps(SelectionKey.OP_READ);
                            } else if (key.isReadable()) {
                                // Optionally handle reading response if needed
                                key.interestOps(SelectionKey.OP_WRITE);
                            }
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

                return connectionTime;
            }
        }

    public static void makeTest(int packetSize, int packetAmount, int requestsCount) {
        System.out.println("\n============================Test started=============================\n");

        System.out.println("Test parameters: \n" +
                "   Number of requests: " + requestsCount +
                "\n   Packets size/amount (per request): size: " + packetSize + ", amount: " + packetAmount);
        CountDownLatch latch = new CountDownLatch(2);

        NonBlockingServer nonBlockingServer = new NonBlockingServer(requestsCount, packetSize,latch);
        nonBlockingServer.start();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        NonBlockingClient nonBlockingClient = new NonBlockingClient(latch, packetSize, packetAmount, requestsCount);
        nonBlockingClient.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("\n=============================Test ended==============================\n");
    }

    public static void main(String[] args) {

        // ==============================MB/s tests=====================================
        makeTest(4096, 5000, 1);
//        makeTest(4096, 100000, 1);
//        makeTest(4096, 2000000, 1);
//        makeTest(20480, 5000, 1);
//        makeTest(20480, 100000, 1);
//        makeTest(20480, 2000000, 1);
//        makeTest(102400, 5000, 1);
//        makeTest(102400, 100000, 1);

        // ========================Latency/connection tests==============================
//        makeTest(20480, 10, 100);
//        makeTest(4096, 10, 100);
    }

}
