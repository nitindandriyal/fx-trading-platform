package pub.lab.trading.common.config;

import org.agrona.collections.Int2ObjectHashMap;

public enum StreamId {

    BOOTSTRAP_STREAM(1),
    RAW_QUOTE(1000),
    MARKET_QUOTE(2000),
    CLIENT_QUOTE(3000),
    HEARTBEAT(8000),
    CONFIG_STREAM(9000);

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
