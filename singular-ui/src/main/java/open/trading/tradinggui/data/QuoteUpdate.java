package open.trading.tradinggui.data;

/**
 * Holds a complete quote update for a currency pair.
 * This is stored in the GuiUpdateThrottler as the latest update.
 */
public record QuoteUpdate(String symbol,
                          double bid,
                          double ask,
                          double[] bidPrices,
                          int[] bidSizes,
                          double[] askPrices,
                          int[] askSizes,
                          long timestamp) {

}

