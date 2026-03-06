package pub.lab.trading.ticketplant;

import org.agrona.DirectBuffer;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

public class Journaler {
    private static final long DEFAULT_MAP_SIZE = 1024L * 1024L * 1024L; // 1GB, adjustable

    private final MappedByteBuffer mappedBuffer;
    private final AtomicLong position = new AtomicLong(0);
    private final Path path;

    public Journaler(String logFilePath) {
        this(logFilePath, DEFAULT_MAP_SIZE);
    }

    public Journaler(String logFilePath, long mapSize) {
        path = Paths.get(logFilePath);
        try {
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            try (RandomAccessFile raf = new RandomAccessFile(logFilePath, "rw")) {
                raf.seek(mapSize - 1);
                raf.writeByte(0); // Pre-allocate by writing to end
                mappedBuffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, mapSize);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to map journal file: " + logFilePath, e);
        }
    }

    public void write(DirectBuffer buffer, int offset, int length) {
        long pos = position.getAndAdd(length);
        if (pos + length > mappedBuffer.capacity()) {
            throw new RuntimeException("Journal capacity exceeded. Consider larger map or rollover.");
        }
        buffer.getBytes(offset, mappedBuffer, (int) pos, length);
    }

    public void force() {
        mappedBuffer.force(); // Flush to disk
    }

    public void close() {
        // MappedByteBuffer doesn't have close, but force changes and let GC unmap
        force();
        try {
            // Trim file to actual size if needed
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
                raf.setLength(position.get());
            }
        } catch (IOException e) {
            // Log error
        }
    }
}