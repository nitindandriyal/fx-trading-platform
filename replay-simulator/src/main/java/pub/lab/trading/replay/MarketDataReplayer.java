package pub.lab.trading.replay;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.model.sbe.CurrencyPair;
import play.lab.model.sbe.MarketTickEncoder;
import play.lab.model.sbe.MessageHeaderEncoder;
import pub.lab.trading.common.messaging.AeronBus;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MarketDataReplayer implements Runnable, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketDataReplayer.class);

    private final AeronBus out;
    private final MarketTickEncoder marketTickEncoder;

    private final Connection conn;
    private final PreparedStatement ps;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final IdleStrategy idle = new BackoffIdleStrategy();

    private ResultSet rs;
    private volatile Throwable lastError;

    private final MutableDirectBuffer pubBuf = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

    private boolean pending = false;
    private int pendingLen = 0;

    public MarketDataReplayer(
            final AeronBus out,
            final MarketTickEncoder marketTickEncoder,
            final String jdbcUrl,
            final String user,
            final String password,
            final String tableName,
            final long fromCreationTsMicrosInclusive,
            final long toCreationTsMicrosExclusive,
            final int fetchSize
    ) throws SQLException {
        this.out = Objects.requireNonNull(out, "out");
        this.marketTickEncoder = Objects.requireNonNull(marketTickEncoder, "encoder");

        this.conn = DriverManager.getConnection(jdbcUrl, user, password);
        this.conn.setAutoCommit(false);

        this.ps = conn.prepareStatement(defaultSql(tableName));
        this.ps.setFetchSize(fetchSize);
        this.ps.setLong(1, fromCreationTsMicrosInclusive);
        this.ps.setLong(2, toCreationTsMicrosExclusive);
    }

    public void stop() {
        running.set(false);
    }

    public Throwable lastError() {
        return lastError;
    }

    @Override
    public void run() {
        try {
            rs = ps.executeQuery();
        } catch (Throwable t) {
            lastError = t;
            running.set(false);
        }

        while (running.get()) {
            try {
                int work = replayStep();
                idle.idle(work);
            } catch (Throwable t) {
                lastError = t;
                running.set(false);
            }
        }
        close();
    }

    private int replayStep() throws SQLException {
        if (pending) {
            if (out.publish(pubBuf, 0, pendingLen)) {
                pending = false;
                pendingLen = 0;
                return 1;
            }
            return 0;
        }

        if (rs == null) return 0;

        if (!rs.next()) {
            running.set(false);
            return 1;
        }

        final CurrencyPair symbol = CurrencyPair.get(rs.getShort(1));
        final long creationTsMicros = rs.getLong(2);
        final int tenor = rs.getInt(3);
        final long valueDate = rs.getLong(4);
        final long clientTier = rs.getLong(5);
        final double bid = rs.getDouble(6);
        final double ask = rs.getDouble(7);
        final double volume = rs.getDouble(8);
        final short level = rs.getShort(9);

        marketTickEncoder.wrapAndApplyHeader(pubBuf, 0, new MessageHeaderEncoder())
                .symbol(symbol)
                .priceCreationTimestamp(creationTsMicros)
                .tenor(tenor)
                .valueDate(valueDate)
                .clientTier(clientTier)
                .bid(bid)
                .ask(ask)
                .volume(volume)
                .level(level);

        boolean result = out.publish(pubBuf, 0, marketTickEncoder.encodedLength());

        if (result) {
            LOGGER.debug("Published tick for {} @ {}: tenor={}, valueDate={}, tier={}, bid={}, ask={}, vol={} level={}",
                    symbol.name(),
                    creationTsMicros,
                    tenor,
                    valueDate,
                    clientTier,
                    bid,
                    ask,
                    volume,
                    level);
        } else {
            LOGGER.error("Failed to publish tick for {} @ {}: tenor={}, valueDate={}, tier={}, bid={}, ask={}, vol={} level={}",
                    symbol.name(),
                    creationTsMicros,
                    tenor,
                    valueDate,
                    clientTier,
                    bid,
                    ask,
                    volume,
                    level);
        }
        return 1;
    }

    @Override
    public void close() {
        try {
            if (rs != null) rs.close();
        } catch (Exception ignore) {
        }
        try {
            ps.close();
        } catch (Exception ignore) {
        }
        try {
            conn.close();
        } catch (Exception ignore) {
        }
    }

    /**
     * Handy default SQL generator (filtering by the event time column you store: priceCreationTimestamp).
     */
    public static String defaultSql(String tableName) {
        return "SELECT symbol, priceCreationTimestamp, tenor, valueDate, clientTier, bid, ask, volume, level " +
                "FROM " + tableName + " " +
                "WHERE priceCreationTimestamp >= ? AND priceCreationTimestamp < ? " +
                "ORDER BY priceCreationTimestamp ASC";
    }
}
