package pub.lab.trading.common.util;

import play.lab.model.sbe.Currency;
import play.lab.model.sbe.CurrencyPair;

public class CurrencyMapper {
    // High-performance lookup arrays
    private static final byte[] BASE_MAP = new byte[256];
    private static final byte[] TERM_MAP = new byte[256];

    static {
        // Initialize maps with NULL/Default
        for (int i = 0; i < 256; i++) {
            BASE_MAP[i] = (byte) Currency.NULL_VAL.value();
            TERM_MAP[i] = (byte) Currency.NULL_VAL.value();
        }

        // Map Pair -> (Base, Term)
        map(CurrencyPair.EURUSD, Currency.EUR, Currency.USD);
        map(CurrencyPair.USDJPY, Currency.USD, Currency.JPY);
        map(CurrencyPair.GBPUSD, Currency.GBP, Currency.USD);
        map(CurrencyPair.AUDUSD, Currency.AUD, Currency.USD);
        map(CurrencyPair.USDCAD, Currency.USD, Currency.CAD);
        map(CurrencyPair.USDCHF, Currency.USD, Currency.CHF);
        map(CurrencyPair.NZDUSD, Currency.NZD, Currency.USD);

        // Minor Crosses
        map(CurrencyPair.EURGBP, Currency.EUR, Currency.GBP);
        map(CurrencyPair.EURJPY, Currency.EUR, Currency.JPY);
        map(CurrencyPair.EURCHF, Currency.EUR, Currency.CHF);
        map(CurrencyPair.GBPJPY, Currency.GBP, Currency.JPY);
        map(CurrencyPair.AUDJPY, Currency.AUD, Currency.JPY);

        // Exotic & Commodities
        map(CurrencyPair.USDCNH, Currency.USD, Currency.CNH);
        map(CurrencyPair.USDMXN, Currency.USD, Currency.MXN);
        map(CurrencyPair.XAUUSD, Currency.XAU, Currency.USD);
        map(CurrencyPair.XAGUSD, Currency.XAG, Currency.USD);
        // ... add remaining pairs following this pattern
    }

    private static void map(CurrencyPair pair, Currency base, Currency term) {
        BASE_MAP[pair.value()] = (byte) base.value();
        TERM_MAP[pair.value()] = (byte) term.value();
    }

    /**
     * Returns the Base currency code for a given Pair value.
     */
    public static Currency getBase(int pairValue) {
        return Currency.get(BASE_MAP[pairValue & 0xFF]);
    }

    /**
     * Returns the Term (Quote) currency code for a given Pair value.
     */
    public static Currency getTerm(int pairValue) {
        return Currency.get(TERM_MAP[pairValue & 0xFF]);
    }
}
