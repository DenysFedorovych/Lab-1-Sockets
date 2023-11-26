package org.example.http.nonblocking;

import org.example.http.Client;
import org.example.http.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2018-Apr-24
 */
public class NonBlockingWRServer extends Server
{
    private ServerSocketChannel serverChannel;
    private Selector selector;

    private ByteBuffer buffer = ByteBuffer.allocate(2100000);
    /**
     * This HashMap is important. It keeps track of the data that will be written to the clients.
     * This is needed because we read/write asynchronously and we might be reading while the server
     * wants to write. In other words, we tell the Selector we are ready to write (SelectionKey.OP_WRITE)
     * and when we get a key for writing, we then write from the HashMap. The write() method explains this further.
     */
    private Map<SocketChannel, byte[]> socketChannelToWriteData = new HashMap<>();

    public NonBlockingWRServer(int port, Client client) {
        super(port, client);
        init();
        this.workingThread = createWorkingThread();
    }

    private void init() {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress("127.0.0.1", port));

            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Thread createWorkingThread() {
        return new Thread(() -> {
            try {

                while (!Thread.currentThread().isInterrupted()) {
                    selector.select();

                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();

                        keys.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isAcceptable()) {
                            accept(key);
                        }

                        if (key.isWritable()) {
                            write(key);
                        }

                        if (key.isReadable()) {
                            read(key);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void stop() {
        super.stop();
        if (selector != null) {
            try {
                selector.close();
                serverChannel.socket().close();
                serverChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Since we are accepting, we must instantiate a serverSocketChannel by calling key.channel().
     * We use this in order to get a socketChannel (which is like a socket in I/O) by calling
     * serverSocketChannel.accept() and we register that channel to the selector to listen
     * to a WRITE OPERATION. I do this because my server sends a hello message to each
     * client that connects to it. This doesn't mean that I will write right NOW. It means that I
     * told the selector that I am ready to write and that next time Selector.select() gets called
     * it should give me a key with isWritable(). More on this in the write() method.
     */
    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        socketChannel.register(selector, SelectionKey.OP_READ);
    }

    /**
     * We read data from the channel. In this case, my server works as an echo, so it calls the echo() method.
     * The echo() method, sets the server in the WRITE OPERATION. When the while loop in run() happens again,
     * one of those keys from Selector.select() will be key.isWritable() and this is where the actual
     * write will happen by calling the write() method.
     */
    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        buffer.clear();

        int read;
        try {
            read = channel.read(buffer);
        } catch (IOException e) {
            key.cancel();
            channel.close();
            return;
        }

        if (read == -1) {
            channel.close();
            key.cancel();
            return;
        }

        buffer.flip();
        byte[] data = new byte[read];
        buffer.get(data, 0, read);

        socketChannelToWriteData.put(channel, responseGeneratorFunction.apply(data));
        key.interestOps(SelectionKey.OP_WRITE);
    }

    /**
     * We registered this channel in the Selector. This means that the SocketChannel we are receiving
     * back from the key.channel() is the same channel that was used to register the selector in the accept()
     * method. Again, I am just explaining as if things are synchronous to make things easy to understand.
     * This means that later, we might register to write from the read() method (for example).
     */
    private void write(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        byte[] data = socketChannelToWriteData.remove(channel);

        channel.write(ByteBuffer.wrap(data));

        key.interestOps(SelectionKey.OP_READ);
    }
}