package play.lab.config.service;

import play.lab.marketdata.model.MarketDataTick;

/**
 * Listener for real-time price updates from Aeron market data stream.
 * Implementers can subscribe to price updates for immediate UI refresh.
 *
 * Implementations should be thread-safe and non-blocking, as this is called
 * from high-frequency Aeron polling threads.
 */
public interface PriceUpdateListener {
    /**
     * Called when a new price tick arrives from the market data stream.
     *
     * @param tick The market data tick with updated prices
     */
    void onPriceUpdate(MarketDataTick tick);
}

