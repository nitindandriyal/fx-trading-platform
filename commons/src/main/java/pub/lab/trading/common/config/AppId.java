package pub.lab.trading.common.config;

import org.agrona.collections.Int2ObjectHashMap;

public enum AppId {
    AERON_MEDIA_DRIVER((short) 0),
    CONFIG_SERVICE((short) 1),
    QUOTING_ENGINE((short) 2),
    PRICING_ENGINE((short) 3),
    MARKET_DATA((short) 4),
    STANDARD_ADAPTER((short) 5);

    private static final Int2ObjectHashMap<AppId> MAP = new Int2ObjectHashMap<>();

    static {
        for (AppId t : values()) {
            MAP.put(t.code, t);
        }
    }

    private final short code;

    AppId(short code) {
        this.code = code;
    }

    public static AppId fromCode(int code) {
        return MAP.get(code);
    }

    public short getCode() {
        return code;
    }
}
