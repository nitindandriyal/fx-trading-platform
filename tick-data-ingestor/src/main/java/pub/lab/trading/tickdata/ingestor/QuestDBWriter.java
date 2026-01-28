package pub.lab.trading.tickdata.ingestor;

import io.questdb.client.Sender;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pub.lab.trading.common.lifecycle.ArrayObjectPool;
import pub.lab.trading.common.lifecycle.Worker;

public class QuestDBWriter implements AutoCloseable, Worker {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestDBWriter.class);

    private final OneToOneConcurrentArrayQueue<MarketTickCodec> marketTickCodecs = new OneToOneConcurrentArrayQueue<>(256);
    private final ArrayObjectPool<MarketTickCodec> marketTickCodecObjectPool = new ArrayObjectPool<>("MarketTickCodec-Pool", MarketTickCodec::new);
    // Configured for ILP over TCP/HTTP
    private final Sender sender;

    public QuestDBWriter(String config) {
        this.sender = Sender.fromConfig(config);
    }

    public void flush() {
        sender.flush();
    }

    @Override
    public void close() {
        sender.close();
    }

    @Override
    public int doWork() {
        MarketTickCodec pooledReusableCodec = marketTickCodecs.poll();
        if (pooledReusableCodec != null) {
            try {
                return pooledReusableCodec.decodeAndSend(sender);
            } finally {
                marketTickCodecObjectPool.release(pooledReusableCodec);
            }
        }
        return 0;
    }

    public void writeQuote(String name,
                           long timestamp,
                           int tenor,
                           long valueDate,
                           long clientTier,
                           double bid,
                           double ask,
                           double volume,
                           short i) {
        MarketTickCodec pooledReusableCodec = marketTickCodecObjectPool.get();
        try {
            pooledReusableCodec.encode(
                    0,
                    name,
                    timestamp,
                    tenor,
                    valueDate,
                    clientTier,
                    bid,
                    ask,
                    volume,
                    i
            );
            marketTickCodecs.offer(pooledReusableCodec);
            LOGGER.debug("Queued tick for {} @ {}: tenor={}, valueDate={}, tier={}, bid={}, ask={}, vol={} level={}",
                    name,
                    timestamp,
                    tenor,
                    valueDate,
                    clientTier,
                    bid,
                    ask,
                    volume,
                    i);
        } catch (Exception e) {
            LOGGER.error("Failed to encode market tick {}", pooledReusableCodec, e);
        }
    }

    @Override
    public String roleName() {
        return "QuestDBWriter";
    }
}
