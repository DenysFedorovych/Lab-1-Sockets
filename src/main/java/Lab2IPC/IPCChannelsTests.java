package Lab2IPC;

import java.util.Random;

public class IPCChannelsTests {

    interface IPCChannel {
        /**
         * Performs write operation through IPCChannel
         * @param data - byte array to write through channel
         */
        void write(byte[] data);

        /**
         * Performs read operation through IPCChannel
         * @param buffer - byte array to read data to
         */
        void read(byte[] buffer);

        /**
         * Closes the channel, deleting all the leavings after channel usage
         */
        void close();

        /**
         * Clears the channel, removing all the written data
         */
        void clear();

        /**
         * Returns the maximum capacity of the channel (maximum bytes amount that can be held in the channel)
         */
        default long getMaxCapacity() {
            return 0;
        };
    }

    private final IPCChannel readerChannel;
    private final IPCChannel writerChannel;

    public IPCChannelsTests(IPCChannel readerChannel, IPCChannel writerChannel) {
        this.readerChannel = readerChannel;
        this.writerChannel = writerChannel;
    }

    public void makeTest() {
        testLatency(10, 1000);
        testLatency(100, 100);
        testLatency(1000, 10);

        testLatency(10, 1000);
        testLatency(100, 100);
        testLatency(1000, 10);

        testThroughput(1024, 1, 10);
        testThroughput(4096, 1, 10);
        testThroughput(20480, 1, 10);
        testThroughput(102400, 1, 10);

        testThroughput(1024, 1, 100);
        testThroughput(4096, 1, 100);
        testThroughput(20480, 1, 100);
        testThroughput(102400, 1, 100);

        testThroughput(1024, 100, 100);
        testThroughput(4096, 100, 100);
        testThroughput(20480, 100, 100);
        testThroughput(102400, 100, 100);

        readerChannel.close();
    }

    private void testLatency(int packetSize, int numberOfTests) {
        long writeLatencySum = 0;
        long readLatencySum = 0;

        byte[] packet = new byte[packetSize];
        new Random().nextBytes(packet);

        byte[] buffer = new byte[packetSize];


        for (int i = 0; i < numberOfTests; i++) {
            long beforeWrite = System.nanoTime();
            writerChannel.write(packet);
            long afterWrite = System.nanoTime();
            readerChannel.read(buffer);
            long afterRead = System.nanoTime();

            writeLatencySum += afterWrite - beforeWrite;
            readLatencySum += afterRead - afterWrite;
        }

        writerChannel.clear();
        readerChannel.clear();

        System.out.println("Latency test: packet size = " + packetSize);
        System.out.println("Average write latency: " + ((double) writeLatencySum / numberOfTests) + " ns");
        System.out.println("Average read latency: " + ((double) readLatencySum / numberOfTests) + " ns");
    }

    private void testThroughput(int packetSize, int packetAmount, int numberOfTests) {
        byte[] packet = new byte[packetSize];
        new Random().nextBytes(packet);
        byte[] buffer = new byte[packetSize];

        long writeTimeSum = 0;
        long readTimeSum = 0;

        for (int j = 0; j < numberOfTests; j++) {
            long beforeWrite = System.nanoTime();
            for (int i = 0; i < packetAmount; i++) {
                writerChannel.write(packet);
            }
            long afterWrite = System.nanoTime();
            for (int i = 0; i < packetAmount; i++) {
                readerChannel.read(buffer);
            }
            long afterRead = System.nanoTime();

            writeTimeSum += afterWrite - beforeWrite;
            readTimeSum += afterRead - afterWrite;

            writerChannel.clear();
            readerChannel.clear();
        }

        double bytesTransferred = (double) packetSize * packetAmount * numberOfTests;


        System.out.println("Throughput test: packet size = " + packetSize + ", packet amount = " + packetAmount);
        System.out.println("Write throughput (Mb/s): " + (bytesTransferred * 1000 / writeTimeSum));
        System.out.println("Read throughput (Mb/s): " + (bytesTransferred * 1000/ readTimeSum));
    }
}
