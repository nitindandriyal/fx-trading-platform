package pub.lab.trading.common.config;

import org.agrona.collections.Int2ObjectHashMap;

public enum EnvId {
    LOCAL((short) 0),
    DEV((short) 1),
    QA((short) 2),
    INT((short) 3),
    PRE_PROD((short) 4),
    PROD((short) 5);

    private static final Int2ObjectHashMap<EnvId> MAP = new Int2ObjectHashMap<>();

    static {
        for (EnvId t : values()) {
            MAP.put(t.code, t);
        }
    }

    private final short code;

    EnvId(short code) {
        this.code = code;
    }

    public static EnvId fromCode(int code) {
        return MAP.get(code);
    }

    public short getCode() {
        return code;
    }
}
