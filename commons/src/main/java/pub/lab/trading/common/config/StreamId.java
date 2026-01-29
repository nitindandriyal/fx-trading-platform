package pub.lab.trading.common.config;

import org.agrona.collections.Int2ObjectHashMap;

public enum StreamId {

    BOOTSTRAP_STREAM(1),
    HEARTBEAT(2),

    // Data Streams,
    CONTROL_CONFIG_STREAM(1000),
    CONTROL_RAW_QUOTE(2000),
    CONTROL_MARKET_QUOTE(3000),
    CONTROL_CLIENT_QUOTE(4000),

    // Control Streams
    DATA_CONFIG_STREAM(1100),
    DATA_RAW_QUOTE(2100),
    DATA_MARKET_QUOTE(3100),
    DATA_CLIENT_QUOTE(4100),

    NONE(-1); // end of streams

    private static final Int2ObjectHashMap<StreamId> MAP = new Int2ObjectHashMap<>();

    static {
        for (StreamId t : values()) {
            MAP.put(t.code, t);
        }
    }

    private final int code;

    StreamId(int code) {
        this.code = code;
    }

    public static StreamId fromCode(int code) {
        return MAP.get(code);
    }

    public int getCode() {
        return code;
    }
}
