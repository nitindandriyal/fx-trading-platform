package play.lab.config.service;

import io.aeron.Subscription;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.marketdata.model.MarketDataTick;
import pub.lab.trading.common.lifecycle.Worker;
import pub.lab.trading.common.model.pricing.QuoteView;
import play.lab.model.sbe.MessageHeaderDecoder;

/**
 * Worker that subscribes to market data stream (Aeron) and notifies registered PriceUpdateListeners.
 * Operates at high frequency (30+ Hz) for live price updates.
 *
 * This poller decodes Quote messages from the Aeron market data stream and converts them to
 * MarketDataTick objects for UI consumption.
 */
public class MarketDataPoller implements Worker, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketDataPoller.class);

    private final Subscription subscription;
    private final AeronService aeronService;
    private boolean connected = false;

    /**
     * Creates a new MarketDataPoller.
     *
     * @param subscription The Aeron subscription to market data stream
     * @param aeronService The AeronService to notify listeners
     */
    public MarketDataPoller(Subscription subscription, AeronService aeronService) {
        this.subscription = subscription;
        this.aeronService = aeronService;
    }

    @Override
    public int doWork() {
        if (!subscription.isConnected()) {
            new NoOpIdleStrategy().idle();
            return 0;
        }

        if (!connected) {
            LOGGER.info("✅ Market data subscription connected streamId={} channel={}",
                    subscription.streamId(), subscription.channel());
            connected = true;
        }

        // Poll for market data fragments
        // Process up to 10 fragments per doWork cycle
        return subscription.poll((buffer, offset, length, header) -> {
            try {
                onMarketDataFragment(buffer, offset, length);
            } catch (Exception e) {
                LOGGER.error("Error processing market data fragment", e);
            }
        }, 10);
    }

    /**
     * Handles incoming market data fragment.
     * Decodes Quote message and notifies listeners.
     */
    private void onMarketDataFragment(DirectBuffer buffer, int offset, int length) {
        try {
            // Decode the Quote message using QuoteView
            // Skip message header (8 bytes)
            QuoteView quoteView = new QuoteView();
            quoteView.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH);

            // Get the rung decoder for bid/ask levels
            play.lab.model.sbe.QuoteMessageDecoder.RungDecoder rungDecoder = quoteView.getRung();

            if (rungDecoder.hasNext()) {
                // Get best rung (level 0)
                rungDecoder.next();
                double bid = rungDecoder.bid();
                double ask = rungDecoder.ask();

                // Create MarketDataTick from decoded data
                MarketDataTick tick = new MarketDataTick(
                    quoteView.getSymbol(),              // CurrencyPair
                    (bid + ask) / 2.0,                  // mid price
                    bid,                                // bid
                    ask,                                // ask
                    quoteView.getValueDate(),           // value date
                    quoteView.priceCreationTimestamp()  // timestamp
                );

                // Notify all listeners
                aeronService.notifyPriceUpdate(tick);
            }
        } catch (Exception e) {
            LOGGER.error("Error decoding market data fragment at offset {}, length {}", offset, length, e);
        }
    }

    @Override
    public String roleName() {
        return "MarketDataPoller";
    }

    @Override
    public void close() {
        LOGGER.info("Closing MarketDataPoller");
        // Note: Don't close subscription - it's owned by Aeron
    }
}

