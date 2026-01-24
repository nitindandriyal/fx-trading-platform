package play.lab;

import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.agrona.MarkFile;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pub.lab.trading.common.config.AeronConfigs;

import java.io.File;

public class StandaloneArchivingMediaDriver {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneArchivingMediaDriver.class);

    public static void main(String[] args) {
        startMediaDriverWithArchive();
    }

    private static void startMediaDriverWithArchive() {

        System.setProperty("aeron.event.log", "admin,publication,subscription");

        MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
                .aeronDirectoryName(AeronConfigs.AERON_LIVE_DIR)
                .threadingMode(ThreadingMode.DEDICATED)
                .spiesSimulateConnection(true)
                .dirDeleteOnStart(true)
                .warnIfDirectoryExists(true)
                .termBufferSparseFile(true); // for Windows compatibility

        Archive.Context archiveContext = new Archive.Context()
                .archiveDir(new File(AeronConfigs.AERON_ARCHIVE_DIR)) // Set directory for archive recordings
                .threadingMode(ArchiveThreadingMode.SHARED)
                .deleteArchiveOnStart(true)
                .controlChannel(AeronConfigs.CONTROL_REQUEST_CHANNEL) // IPC channel for archive control requests
                .replicationChannel(AeronConfigs.LIVE_CHANNEL); // IPC channel for archive replication

        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        try (ArchivingMediaDriver archive = ArchivingMediaDriver.launch(mediaDriverContext, archiveContext)) {
            LOGGER.info("🚀 Launching Aeron MediaDriver: {} {}", mediaDriverContext.aeronDirectory(), archiveContext.aeronDirectoryName());
            LOGGER.info("📡 Archive Control Channel: {}", archiveContext.controlChannel());
            LOGGER.info("🔁 Replication Channel: {}", archiveContext.replicationChannel());
            LOGGER.info("📡 MediaDriver running. Ctrl+C to terminate...");
            barrier.await();
            LOGGER.info("🛑 Shutting down MediaDriver...");
            CloseHelper.quietClose(archive);
            LOGGER.info("✅ MediaDriver shutdown complete.");
        }
    }
}
