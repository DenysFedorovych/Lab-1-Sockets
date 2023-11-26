package org.example.http;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public abstract class Server {

    protected final int port;

    protected Function<byte[], byte[]> responseGeneratorFunction;
    protected Thread workingThread;

    protected final AtomicLong receivedBytes = new AtomicLong(0);
    protected final AtomicLong sentBytes = new AtomicLong(0);

    public Server(int port, Client client) {
        this.port = port;
        this.responseGeneratorFunction = client.responseProvider.getResponseGeneratorFunction();
    }

    public void start() {
        workingThread.start();
    }

    public void stop() {
        workingThread.interrupt();
        workingThread = null;
    }
}
