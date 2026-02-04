package pub.lab.trading.common.model.config;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A GC-free flyweight for reading and writing ClientTier SBE messages.
 * Operates directly on a DirectBuffer to avoid allocations, supporting
 * high-performance encoding and decoding for the FX trading platform.
 */
public class ClientTierFlyweight {
    private static final int MESSAGE_ID = 4;
    private static final int MESSAGE_SIZE = 128;
    private static final int MAX_TIER_NAME_LENGTH = 64;

    // Field offsets relative to the message start
    private static final int MESSAGE_ID_OFFSET = 0; // int (4 bytes)
    private static final int TIER_ID_OFFSET = 4; // uint16 (2 bytes)
    private static final int TIER_NAME_LENGTH_OFFSET = 6; // byte (1 byte)
    private static final int TIER_NAME_OFFSET = 7; // string64 (64 bytes)
    private static final int MARKUP_BPS_OFFSET = 71; // double (8 bytes)
    private static final int SPREAD_TIGHTENING_FACTOR_OFFSET = 79; // double
    private static final int QUOTE_THROTTLE_MS_OFFSET = 87; // uint32 (4 bytes)
    private static final int LATENCY_PROTECTION_MS_OFFSET = 91; // uint32
    private static final int QUOTE_EXPIRY_MS_OFFSET = 95; // uint32
    private static final int MIN_NOTIONAL_OFFSET = 99; // double
    private static final int MAX_NOTIONAL_OFFSET = 107; // double
    private static final int PRICE_PRECISION_OFFSET = 115; // uint8 (1 byte)
    private static final int STREAMING_ENABLED_OFFSET = 116; // BooleanEnum (1 byte)
    private static final int LIMIT_ORDER_ENABLED_OFFSET = 117; // BooleanEnum
    private static final int ACCESS_TO_CROSSES_OFFSET = 118; // BooleanEnum
    private static final int CREDIT_LIMIT_USD_OFFSET = 119; // double
    private static final int TIER_PRIORITY_OFFSET = 127; // uint8
    private final byte[] tempNameBuffer = new byte[MAX_TIER_NAME_LENGTH]; // Reusable for string conversion
    private DirectBuffer buffer;
    private MutableDirectBuffer mutableBuffer;
    private int offset;

    /**
     * Gets the size of the ClientTier message.
     *
     * @return the message size in bytes
     */
    public static int messageSize() {
        return MESSAGE_SIZE;
    }

    /**
     * Wraps a buffer for reading or writing at the specified offset.
     *
     * @param buffer the buffer to wrap
     * @param offset the offset of the ClientTier message
     * @return this flyweight for chaining
     */
    public ClientTierFlyweight wrap(DirectBuffer buffer, int offset) {
        this.buffer = buffer;
        this.mutableBuffer = null;
        this.offset = offset;
        return this;
    }

    /**
     * Wraps a mutable buffer for writing at the specified offset.
     *
     * @param buffer the mutable buffer to wrap
     * @param offset the offset of the ClientTier message
     * @return this flyweight for chaining
     */
    public ClientTierFlyweight wrap(MutableDirectBuffer buffer, int offset) {
        this.buffer = buffer;
        this.mutableBuffer = buffer;
        this.offset = offset;
        return this;
    }

    // --- View Methods (Getters) ---

    /**
     * Validates that the wrapped buffer contains a ClientTier message.
     *
     * @throws IllegalStateException if the message ID is invalid
     */
    public void validate() {
        if (buffer.getInt(offset + MESSAGE_ID_OFFSET) != MESSAGE_ID) {
            throw new IllegalStateException("Invalid message ID: expected " + MESSAGE_ID);
        }
        int nameLength = buffer.getByte(offset + TIER_NAME_LENGTH_OFFSET) & 0xFF;
        if (nameLength > MAX_TIER_NAME_LENGTH) {
            throw new IllegalStateException("tierName length exceeds 64: " + nameLength);
        }
    }

    public int getTierId() {
        return buffer.getShort(offset + TIER_ID_OFFSET) & 0xFFFF;
    }

    public ClientTierFlyweight setTierId(int tierId) {
        if (tierId < 0 || tierId > 65535) {
            throw new IllegalArgumentException("tierId must be between 0 and 65535");
        }
        checkMutable();
        mutableBuffer.putShort(offset + TIER_ID_OFFSET, (short) tierId);
        return this;
    }

    public String getTierNameAsString() {
        int length = buffer.getByte(offset + TIER_NAME_LENGTH_OFFSET) & 0xFF;
        buffer.getBytes(offset + TIER_NAME_OFFSET, tempNameBuffer, 0, length);
        return new String(tempNameBuffer, 0, length, StandardCharsets.UTF_8);
    }

    public double getMarkupBps() {
        return buffer.getDouble(offset + MARKUP_BPS_OFFSET);
    }

    public ClientTierFlyweight setMarkupBps(double markupBps) {
        if (markupBps < 0 || markupBps > 1000) {
            throw new IllegalArgumentException("markupBps must be between 0 and 1000");
        }
        checkMutable();
        mutableBuffer.putDouble(offset + MARKUP_BPS_OFFSET, markupBps);
        return this;
    }

    public double getSpreadTighteningFactor() {
        return buffer.getDouble(offset + SPREAD_TIGHTENING_FACTOR_OFFSET);
    }

    public ClientTierFlyweight setSpreadTighteningFactor(double spreadTighteningFactor) {
        if (spreadTighteningFactor < 0 || spreadTighteningFactor > 10) {
            throw new IllegalArgumentException("spreadTighteningFactor must be between 0 and 10");
        }
        checkMutable();
        mutableBuffer.putDouble(offset + SPREAD_TIGHTENING_FACTOR_OFFSET, spreadTighteningFactor);
        return this;
    }

    public long getQuoteThrottleMs() {
        return buffer.getInt(offset + QUOTE_THROTTLE_MS_OFFSET) & 0xFFFFFFFFL;
    }

    public ClientTierFlyweight setQuoteThrottleMs(long quoteThrottleMs) {
        if (quoteThrottleMs < 0 || quoteThrottleMs > 4294967295L) {
            throw new IllegalArgumentException("quoteThrottleMs must be between 0 and 4294967295");
        }
        checkMutable();
        mutableBuffer.putInt(offset + QUOTE_THROTTLE_MS_OFFSET, (int) quoteThrottleMs);
        return this;
    }

    public long getLatencyProtectionMs() {
        return buffer.getInt(offset + LATENCY_PROTECTION_MS_OFFSET) & 0xFFFFFFFFL;
    }

    public ClientTierFlyweight setLatencyProtectionMs(long latencyProtectionMs) {
        if (latencyProtectionMs < 0 || latencyProtectionMs > 4294967295L) {
            throw new IllegalArgumentException("latencyProtectionMs must be between 0 and 4294967295");
        }
        checkMutable();
        mutableBuffer.putInt(offset + LATENCY_PROTECTION_MS_OFFSET, (int) latencyProtectionMs);
        return this;
    }

    public long getQuoteExpiryMs() {
        return buffer.getInt(offset + QUOTE_EXPIRY_MS_OFFSET) & 0xFFFFFFFFL;
    }

    public ClientTierFlyweight setQuoteExpiryMs(long quoteExpiryMs) {
        if (quoteExpiryMs < 0 || quoteExpiryMs > 4294967295L) {
            throw new IllegalArgumentException("quoteExpiryMs must be between 0 and 4294967295");
        }
        checkMutable();
        mutableBuffer.putInt(offset + QUOTE_EXPIRY_MS_OFFSET, (int) quoteExpiryMs);
        return this;
    }

    public double getMinNotional() {
        return buffer.getDouble(offset + MIN_NOTIONAL_OFFSET);
    }

    // --- Writer Methods (Setters) ---

    public ClientTierFlyweight setMinNotional(double minNotional) {
        if (minNotional < 0) {
            throw new IllegalArgumentException("minNotional must be non-negative");
        }
        double maxNotional = mutableBuffer.getDouble(offset + MAX_NOTIONAL_OFFSET);
        if (minNotional > maxNotional && maxNotional != 0) {
            throw new IllegalArgumentException("minNotional must not exceed maxNotional");
        }
        checkMutable();
        mutableBuffer.putDouble(offset + MIN_NOTIONAL_OFFSET, minNotional);
        return this;
    }

    public double getMaxNotional() {
        return buffer.getDouble(offset + MAX_NOTIONAL_OFFSET);
    }

    public ClientTierFlyweight setMaxNotional(double maxNotional) {
        if (maxNotional < 0) {
            throw new IllegalArgumentException("maxNotional must be non-negative");
        }
        double minNotional = mutableBuffer.getDouble(offset + MIN_NOTIONAL_OFFSET);
        if (minNotional > maxNotional) {
            throw new IllegalArgumentException("maxNotional must not be less than minNotional");
        }
        checkMutable();
        mutableBuffer.putDouble(offset + MAX_NOTIONAL_OFFSET, maxNotional);
        return this;
    }

    public byte getPricePrecision() {
        return buffer.getByte(offset + PRICE_PRECISION_OFFSET);
    }

    public ClientTierFlyweight setPricePrecision(byte pricePrecision) {
        if (pricePrecision < 0) {
            throw new IllegalArgumentException("pricePrecision must be non-negative");
        }
        checkMutable();
        mutableBuffer.putByte(offset + PRICE_PRECISION_OFFSET, pricePrecision);
        return this;
    }

    public boolean isStreamingEnabled() {
        return buffer.getByte(offset + STREAMING_ENABLED_OFFSET) != 0;
    }

    public ClientTierFlyweight setStreamingEnabled(boolean streamingEnabled) {
        checkMutable();
        mutableBuffer.putByte(offset + STREAMING_ENABLED_OFFSET, (byte) (streamingEnabled ? 1 : 0));
        return this;
    }

    public boolean isLimitOrderEnabled() {
        return buffer.getByte(offset + LIMIT_ORDER_ENABLED_OFFSET) != 0;
    }

    public ClientTierFlyweight setLimitOrderEnabled(boolean limitOrderEnabled) {
        checkMutable();
        mutableBuffer.putByte(offset + LIMIT_ORDER_ENABLED_OFFSET, (byte) (limitOrderEnabled ? 1 : 0));
        return this;
    }

    public boolean isAccessToCrosses() {
        return buffer.getByte(offset + ACCESS_TO_CROSSES_OFFSET) != 0;
    }

    public ClientTierFlyweight setAccessToCrosses(boolean accessToCrosses) {
        checkMutable();
        mutableBuffer.putByte(offset + ACCESS_TO_CROSSES_OFFSET, (byte) (accessToCrosses ? 1 : 0));
        return this;
    }

    public double getCreditLimitUsd() {
        return buffer.getDouble(offset + CREDIT_LIMIT_USD_OFFSET);
    }

    public ClientTierFlyweight setCreditLimitUsd(double creditLimitUsd) {
        if (creditLimitUsd < 0) {
            throw new IllegalArgumentException("creditLimitUsd must be non-negative");
        }
        checkMutable();
        mutableBuffer.putDouble(offset + CREDIT_LIMIT_USD_OFFSET, creditLimitUsd);
        return this;
    }

    public byte getTierPriority() {
        return buffer.getByte(offset + TIER_PRIORITY_OFFSET);
    }

    public ClientTierFlyweight setTierPriority(byte tierPriority) {
        if (tierPriority < 0) {
            throw new IllegalArgumentException("tierPriority must be non-negative");
        }
        checkMutable();
        mutableBuffer.putByte(offset + TIER_PRIORITY_OFFSET, tierPriority);
        return this;
    }

    public ClientTierFlyweight setTierName(String tierName) {
        if (tierName == null || tierName.isEmpty()) {
            throw new IllegalArgumentException("tierName is required");
        }
        byte[] nameBytes = tierName.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > MAX_TIER_NAME_LENGTH) {
            throw new IllegalArgumentException("tierName must be 64 bytes or less");
        }
        checkMutable();
        mutableBuffer.putByte(offset + TIER_NAME_LENGTH_OFFSET, (byte) nameBytes.length);
        mutableBuffer.putBytes(offset + TIER_NAME_OFFSET, nameBytes);
        for (int i = nameBytes.length; i < MAX_TIER_NAME_LENGTH; i++) {
            mutableBuffer.putByte(offset + TIER_NAME_OFFSET + i, (byte) 0);
        }
        return this;
    }

    /**
     * Initializes the message with the ClientTier message ID.
     *
     * @return this flyweight for chaining
     */
    public ClientTierFlyweight initMessage() {
        checkMutable();
        mutableBuffer.putInt(offset + MESSAGE_ID_OFFSET, MESSAGE_ID);
        return this;
    }

    private void checkMutable() {
        if (mutableBuffer == null) {
            throw new IllegalStateException("Buffer is not mutable");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ClientTierFlyweight that = (ClientTierFlyweight) o;
        return Objects.equals(getTierId(), that.getTierId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getTierId());
    }
}