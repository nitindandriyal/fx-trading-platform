package pub.lab.trading.common.config;

public class AeronConfigs {

    public static final String LIVE_DIR = "/live-data";
    public static final String AERON_LIVE_DIR = System.getProperty("aeron.base.path") + AeronConfigs.LIVE_DIR;
    public static final String ARCHIVE_DIR = "/archive-data";
    public static final String AERON_ARCHIVE_DIR = System.getProperty("aeron.base.path") + AeronConfigs.ARCHIVE_DIR;
    public static final String CONTROL_REQUEST_CHANNEL = "aeron:udp?endpoint=localhost:18010";
    public static final String CONTROL_RESPONSE_CHANNEL = "aeron:udp?endpoint=localhost:0";
    public static final String LIVE_CHANNEL = "aeron:ipc";
    public static final String CONFIG_CHANNEL = "aeron:ipc";
    public static final String BOOTSTRAP_CHANNEL = "aeron:ipc?endpoint=bootstrap-service";

    private AeronConfigs() {
        // configs
    }

}