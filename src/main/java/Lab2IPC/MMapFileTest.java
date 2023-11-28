package Lab2IPC;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class MMapFileTest {

    static class MmapIPCChannel implements IPCChannelsTests.IPCChannel {

        private final MappedByteBuffer buffer;
        private final String filePath;
        private final RandomAccessFile accessFile;

        public MmapIPCChannel(String filePath, long maxCapacity, boolean isReadOnly) throws IOException {
            this.filePath = filePath;
            accessFile = new RandomAccessFile(filePath, "rw");
            this.buffer = accessFile.getChannel().map(isReadOnly ? FileChannel.MapMode.READ_ONLY : FileChannel.MapMode.READ_WRITE, 0, maxCapacity);
        }

        @Override
        public void write(byte[] data) {
            buffer.put(data);
            buffer.force();
        }

        @Override
        public void read(byte[] buffer) {
            this.buffer.get(buffer, 0, buffer.length);
            this.buffer.force();
        }

        @Override
        public void close() {
            try {
                accessFile.close();
                Files.delete(Path.of(filePath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void clear() {
            // Clearing the content of the buffer
            buffer.clear();
        }

        @Override
        public long getMaxCapacity() {
            return buffer.capacity();
        }
    }

    public static void main(String[] args) {
        try {
            int bufferCapacity = 102400 * 2000;

            IPCChannelsTests ipcTests = new IPCChannelsTests(
                    new MmapIPCChannel("mmap_test.dat", bufferCapacity, true),
                    new MmapIPCChannel("mmap_test.dat", bufferCapacity, false)
            );
            ipcTests.makeTest();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
