package play.lab.pricing.engine.feed;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.model.sbe.CurrencyPair;
import play.lab.model.sbe.MessageHeaderDecoder;
import play.lab.model.sbe.QuoteMessageDecoder;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.config.caches.ClientTierConfig;
import pub.lab.trading.common.config.caches.ClientTierConfigCache;
import pub.lab.trading.common.config.caches.ConfigAgent;
import pub.lab.trading.common.lifecycle.Worker;
import pub.lab.trading.common.model.ClientTierLevel;
import pub.lab.trading.common.model.pricing.QuoteMessageWriter;
import pub.lab.trading.common.model.pricing.QuoteView;

import java.util.EnumMap;

public class SpotPricerPipe implements Worker {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpotPricerPipe.class);

    private final Subscription quoteSub;
    private final EnumMap<ClientTierLevel, Publication> marketQuotePublications = new EnumMap<>(ClientTierLevel.class);
    private final ClientTierConfigCache clientTierConfigCache;
    private final QuoteMessageWriter quoteMessageWriter = new QuoteMessageWriter();
    private final QuoteView quoteView = new QuoteView();
    private final FragmentHandler fragmentHandler;

    public SpotPricerPipe(final Aeron aeron, final ConfigAgent configAgent) {
        this.clientTierConfigCache = configAgent.getClientTierConfigCache();
        this.fragmentHandler = (buf, offset, len, hdr) -> consumeQuotes(buf, offset);
        this.quoteSub = aeron.addSubscription(AeronConfigs.LIVE_CHANNEL,
                StreamId.RAW_QUOTE.getCode(),
                image -> LOGGER.info("Image available: sessionId={}, channel={}, streamId={}",
                        image.sessionId(), image.sourceIdentity(), image.subscription().streamId()),
                image -> LOGGER.warn("Image unavailable: sessionId={}, channel={}, streamId={}",
                        image.sessionId(), image.sourceIdentity(), image.subscription().streamId())
        );
        for (ClientTierLevel clientTierLevel : ClientTierLevel.values()) {
            marketQuotePublications.put(clientTierLevel, aeron.addExclusivePublication(AeronConfigs.LIVE_CHANNEL,
                    StreamId.MARKET_QUOTE.getCode() + clientTierLevel.getId())
            );
        }
        LOGGER.info("Connected Aeron Dir : {} {} {}", aeron.context().aeronDirectory(), quoteSub.channel(), quoteSub.streamId());
    }

    private void consumeQuotes(DirectBuffer buf, int offset) {
        quoteView.wrap(buf, offset + MessageHeaderDecoder.ENCODED_LENGTH);
        CurrencyPair currencyPair = quoteView.getSymbol();
        long timestamp = quoteView.priceCreationTimestamp();
        int tenor = quoteView.getTenor();
        long valueDate = quoteView.getValueDate();
        long clientTier = quoteView.getClientTier();
        QuoteMessageDecoder.RungDecoder rungDecoder = quoteView.getRung();
        while (rungDecoder.hasNext()) {
            QuoteMessageDecoder.RungDecoder nextRung = rungDecoder.next();
            for (ClientTierLevel clientTierLevel : ClientTierLevel.values()) {
                double volume = 1_000_000.0;
                ClientTierConfig clientTierConfig = clientTierConfigCache.get(clientTierLevel.getId());
                double volFactor = Math.log10(nextRung.volume() / volume + 1.0);
                double spreadAdjust = 0.0; // default
                double adjustment = 0.0;
                if (null != clientTierConfig) {
                    spreadAdjust = clientTierConfig.spreadTighteningFactor() * (1 + 0.05 * volFactor);
                    double markupAdjust = clientTierConfig.markupBps() * (1 + 0.1 * volFactor);
                    double skewAdjust = clientTierConfig.tierSkew() * volFactor;
                    adjustment = clientTierConfig.signal() * (markupAdjust + skewAdjust);
                }
                double mid = 0.5 * (nextRung.bid() + nextRung.ask());
                double bid = mid - (spreadAdjust * 0.5) - adjustment;
                double ask = mid + (spreadAdjust * 0.5) + adjustment;

                LOGGER.info("symbol={}, timestamp={}, tenor={}, valueDate={}, clientTier={}, bid={}, ask={}",
                        currencyPair, timestamp, tenor, valueDate, clientTier, bid, ask);
                quoteMessageWriter.beginQuote(
                        currencyPair,
                        valueDate,
                        timestamp,
                        tenor,
                        clientTier,
                        1
                ).addRung(
                        bid,
                        ask,
                        volume
                );
                UnsafeBuffer buffer = quoteMessageWriter.buffer();
                int encodedLength = quoteMessageWriter.encodedLength();

                long result = marketQuotePublications.get(clientTierLevel).offer(buffer, 0, encodedLength);

                if (result < 0) {
                    LOGGER.error("❌ Failed to publish quote to {} : reason {}", marketQuotePublications.get(clientTierLevel), result);
                } else {
                    LOGGER.info("✅ Published quote for");
                }
            }
        }
    }

    @Override
    public int doWork() {
        if (quoteSub.isConnected()) {
            return quoteSub.poll(fragmentHandler, 10);
        }
        return 0;
    }

    @Override
    public void onClose() {
        Worker.super.onClose();
    }

    @Override
    public String roleName() {
        return "MarketDataConsumer";
    }
}
