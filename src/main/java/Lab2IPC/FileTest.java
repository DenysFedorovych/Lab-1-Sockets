package Lab2IPC;

import java.io.*;
import java.nio.file.Files;

public class FileTest implements IPCChannelsTests.IPCChannel {

    private final File file;
    private final FileInputStream inputStream;
    private final FileOutputStream outputStream;

    public FileTest(String filePath) throws IOException {
        this.file = new File(filePath);
        file.delete();
        Files.createFile(file.toPath());
        this.inputStream = new FileInputStream(file);
        this.outputStream = new FileOutputStream(file);
    }

    @Override
    public void write(byte[] data) {
        try {
            outputStream.write(data);
            outputStream.flush();
            outputStream.getChannel().force(false);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to channel", e);
        }
    }

    @Override
    public void read(byte[] buffer) {
        try {
            inputStream.getChannel().force(false);
            inputStream.read(buffer);
        } catch (IOException e) {
            throw new RuntimeException("Error reading from channel", e);
        }
    }

    @Override
    public void close() {
        try {
            inputStream.close();
            outputStream.close();
            file.delete();
        } catch (IOException e) {
            throw new RuntimeException("Error closing channel", e);
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Error clearing channel", e);
        }
    }

    @Override
    public long getMaxCapacity() {
        return file.length();
    }

    public static void main(String[] args) {
        try {
            IPCChannelsTests ipcTests = new IPCChannelsTests(
                    new FileTest("mmap_test"),
                    new FileTest("mmap_test")
            );
            ipcTests.makeTest();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
