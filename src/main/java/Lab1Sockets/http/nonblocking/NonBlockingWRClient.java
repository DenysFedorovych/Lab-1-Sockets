package Lab1Sockets.http.nonblocking;

import Lab1Sockets.http.Client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import static java.nio.channels.SelectionKey.OP_READ;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2018-Apr-24
 */
public class NonBlockingWRClient extends Client implements Runnable
{
    private final static String HOSTNAME = "127.0.0.1";

    public NonBlockingWRClient(int port, int numberOfSendingThreads, ResponseProvider responseProvider) {
        super(port, numberOfSendingThreads, responseProvider);
    }

    @Override
    public int sendDataAndGetResponse(byte[] data) {
        return 0;
    }

    @Override
    public void run() {
        try (SocketChannel channel = SocketChannel.open();
             Selector selector = Selector.open()) {
            channel.configureBlocking(false);

            channel.register(selector, SelectionKey.OP_CONNECT);
            channel.connect(new InetSocketAddress(HOSTNAME, port));

            selector.select(1000);

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) continue;

                if (key.isConnectable()) {
                    connect(key);
                }

                if (key.isWritable()) {
                    write(key);
                }

                if (key.isReadable()) {
                    read(key);
                }
            }

        } catch (IOException ignored) {
        } finally {

        }
    }

//    private void close() {
//        try {
//            selector.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer readBuffer = ByteBuffer.allocate(1000);
        readBuffer.clear();
        int length;
        try {
            length = channel.read(readBuffer);
        } catch (IOException e) {
            System.out.println("Reading problem, closing connection");
            key.cancel();
            channel.close();
            return;
        }
        if (length == -1) {
            System.out.println("Nothing was read from server");
            channel.close();
            key.cancel();
            return;
        }
        readBuffer.flip();
        byte[] buff = new byte[1024];
        readBuffer.get(buff, 0, length);
        System.out.println("Server said: " + new String(buff));
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        channel.write(ByteBuffer.wrap("message".getBytes()));

        // Lets get ready to read.
        key.interestOps(OP_READ);
    }

    private void connect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        if (channel.isConnectionPending()) {
            channel.finishConnect();
        }
        channel.configureBlocking(false);
//        channel.register(selector, OP_WRITE);
    }
}