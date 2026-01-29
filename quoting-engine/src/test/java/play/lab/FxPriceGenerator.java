package play.lab;

import pub.lab.trading.common.util.HolidayCalendar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class FxPriceGenerator {
    private static final double SPREAD_BASIS_POINTS = 0.5; // ~5 pip spread
    private final Map<String, PairModel> pairs = new HashMap<>();

    public FxPriceGenerator() {
        // G10 majors with realistic vols
        add("EURUSD", 1.1000, 0.02); // ~2% annual vol
        add("USDJPY", 145.00, 0.03);
        add("GBPUSD", 1.2500, 0.025);
        add("USDCHF", 0.8800, 0.018);
        add("AUDUSD", 0.6600, 0.028);
        add("NZDUSD", 0.6000, 0.030);
        add("USDCAD", 1.3600, 0.022);
        add("EURJPY", 158.00, 0.025);
        add("EURGBP", 0.8800, 0.017);
        add("EURCHF", 0.9700, 0.015);
    }

    private void add(String pair, double initialPrice, double vol) {
        pairs.put(pair, new PairModel(pair, initialPrice, vol));
    }

    public List<FxTick> generateAll(long now, double dtSeconds) {
        List<FxTick> ticks = new ArrayList<>(pairs.size());
        for (PairModel model : pairs.values()) {
            ticks.add(model.nextTick(now, dtSeconds));
        }
        return ticks;
    }

    public FxTick generate(String pair, long now, double dtSeconds) {
        PairModel model = pairs.get(pair);
        if (model == null) throw new IllegalArgumentException("Unknown pair: " + pair);
        return model.nextTick(now, dtSeconds);
    }

    public Set<String> symbols() {
        return pairs.keySet();
    }

    public static class FxTick {
        public final String pair;
        public final double mid;
        public final double bid;
        public final double ask;
        public final long timestamp;
        public final long valueDateEpoch;

        public FxTick(String pair, double mid, double bid, double ask, long ts, long valueDateEpoch) {
            this.pair = pair;
            this.mid = mid;
            this.bid = bid;
            this.ask = ask;
            this.timestamp = ts;
            this.valueDateEpoch = valueDateEpoch;
        }

        @Override
        public String toString() {
            return String.format("[%s] %.5f (bid: %.5f / ask: %.5f)", pair, mid, bid, ask);
        }
    }

    private static class PairModel {
        final String symbol;
        final double drift;
        final double volatility;
        double price;

        PairModel(String symbol, double initialPrice, double volatility) {
            this.symbol = symbol;
            this.drift = 0.0;
            this.volatility = volatility;
            this.price = initialPrice;
        }

        FxTick nextTick(long now, double dt) {
            double sigma = volatility;
            double z = ThreadLocalRandom.current().nextGaussian();
            double changeFactor = Math.exp((drift - 0.5 * sigma * sigma) * dt + sigma * Math.sqrt(dt) * z);
            price *= changeFactor;

            double spread = price * SPREAD_BASIS_POINTS / 10000.0;
            double bid = price - spread / 2.0;
            double ask = price + spread / 2.0;

            return new FxTick(symbol, price, bid, ask, now, HolidayCalendar.getValueDate());
        }
    }
}
