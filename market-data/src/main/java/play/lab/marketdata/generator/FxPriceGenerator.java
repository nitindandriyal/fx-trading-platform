package play.lab.marketdata.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.TickThrottle;
import play.lab.marketdata.model.MarketDataTick;
import play.lab.marketdata.model.RawPriceConfig;
import play.lab.model.sbe.Currency;
import play.lab.model.sbe.CurrencyPair;
import pub.lab.trading.common.lifecycle.Worker;
import pub.lab.trading.common.util.CachedClock;
import pub.lab.trading.common.util.CurrencyMapper;
import pub.lab.trading.common.util.HolidayCalendar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FxPriceGenerator implements Worker {
    private static final Logger LOGGER = LoggerFactory.getLogger(FxPriceGenerator.class);

    private static final double DEFAULT_SPREAD_BP = 0.5;
    private static final double DEFAULT_VOLATILITY = 0.5;
    private final CachedClock cachedClock;
    private final Map<CurrencyPair, PairModel> pairs = new HashMap<>();
    private final Map<Currency, RawPriceConfig> configOverridesByCcy = new ConcurrentHashMap<>();
    private final RawPriceConfig defaultConfig = new RawPriceConfig(Currency.NULL_VAL, DEFAULT_VOLATILITY, DEFAULT_SPREAD_BP);
    private final QuotePublisher aeronPub = new QuotePublisher();
    private final TickThrottle throttle = new TickThrottle(1000);

    public FxPriceGenerator(final CachedClock cachedClock) {
        this.cachedClock = cachedClock;
        // Volatility overrides (annualized)
        configOverridesByCcy.put(Currency.USD, new RawPriceConfig(Currency.USD, 0.020, 0.5)); // US Dollar
        configOverridesByCcy.put(Currency.EUR, new RawPriceConfig(Currency.EUR, 0.018, 0.5)); // Euro
        configOverridesByCcy.put(Currency.JPY, new RawPriceConfig(Currency.JPY, 0.030, 1.0)); // Japanese Yen
        configOverridesByCcy.put(Currency.GBP, new RawPriceConfig(Currency.GBP, 0.025, 0.6)); // British Pound
        configOverridesByCcy.put(Currency.CHF, new RawPriceConfig(Currency.CHF, 0.017, 0.4)); // Swiss Franc
        configOverridesByCcy.put(Currency.AUD, new RawPriceConfig(Currency.AUD, 0.028, 0.6)); // Australian Dollar
        configOverridesByCcy.put(Currency.NZD, new RawPriceConfig(Currency.NZD, 0.030, 0.7)); // New Zealand Dollar
        configOverridesByCcy.put(Currency.CAD, new RawPriceConfig(Currency.CAD, 0.022, 0.5)); // Canadian Dollar

        // Default major pairs and crosses
        add(CurrencyPair.EURUSD, 1.1000);
        add(CurrencyPair.USDJPY, 145.00);
        add(CurrencyPair.GBPUSD, 1.2500);
        add(CurrencyPair.USDCHF, 0.8800);
        add(CurrencyPair.AUDUSD, 0.6600);
        add(CurrencyPair.NZDUSD, 0.6000);
        add(CurrencyPair.USDCAD, 1.3600);

        // Crosses (most traded)
        add(CurrencyPair.EURJPY, 158.00);
        add(CurrencyPair.EURGBP, 0.8800);
        add(CurrencyPair.EURCHF, 0.9700);
        add(CurrencyPair.GBPJPY, 184.50);
        add(CurrencyPair.AUDJPY, 98.50);
        add(CurrencyPair.NZDJPY, 90.20);
        add(CurrencyPair.CADJPY, 107.30);
        add(CurrencyPair.AUDNZD, 1.0700);
        add(CurrencyPair.EURCAD, 1.4700);
        add(CurrencyPair.GBPCHF, 1.1100);

    }

    private void add(CurrencyPair pair, double initialPrice) {
        double volatility = inferVolatility(pair);
        double spread = inferSpread(pair);
        pairs.put(pair, new PairModel(pair, initialPrice, volatility, spread));
    }

    public void addSymbol(CurrencyPair symbol, double initialPrice, double volatility, double spread) {
        configOverridesByCcy.put(CurrencyMapper.getBase(symbol.value()), new RawPriceConfig(CurrencyMapper.getBase(symbol.value()), volatility, spread));
        configOverridesByCcy.put(CurrencyMapper.getTerm(symbol.value()), new RawPriceConfig(CurrencyMapper.getBase(symbol.value()), volatility, spread));
        pairs.put(symbol, new PairModel(symbol, initialPrice, volatility, spread));
    }

    private double inferVolatility(CurrencyPair pair) {
        Currency base = CurrencyMapper.getBase(pair.value());
        Currency quote = CurrencyMapper.getTerm(pair.value());
        return (configOverridesByCcy.getOrDefault(base, defaultConfig).getVolatility()
                + configOverridesByCcy.getOrDefault(quote, defaultConfig).getVolatility()) / 2;
    }

    private double inferSpread(CurrencyPair pair) {
        Currency base = CurrencyMapper.getBase(pair.value());
        Currency quote = CurrencyMapper.getTerm(pair.value());
        return Math.max(
                configOverridesByCcy.getOrDefault(base, defaultConfig).getSpread(),
                configOverridesByCcy.getOrDefault(quote, defaultConfig).getSpread()
        );
    }

    public double getVol(CurrencyPair pair) {
        Currency base = CurrencyMapper.getBase(pair.value());
        Currency quote = CurrencyMapper.getTerm(pair.value());
        return (configOverridesByCcy.getOrDefault(base, defaultConfig).getVolatility()
                + configOverridesByCcy.getOrDefault(quote, defaultConfig).getVolatility()) * 0.5;
    }

    public double getSpread(CurrencyPair pair) {
        Currency base = CurrencyMapper.getBase(pair.value());
        Currency quote = CurrencyMapper.getTerm(pair.value());
        return Math.max(
                configOverridesByCcy.getOrDefault(base, defaultConfig).getSpread(),
                configOverridesByCcy.getOrDefault(quote, defaultConfig).getSpread()
        );
    }

    public void updateModel(CurrencyPair pair, double vol, double spread) {
        Currency base = CurrencyMapper.getBase(pair.value());
        configOverridesByCcy.compute(base, (k, oldValue) -> {
            if (oldValue == null) {
                return new RawPriceConfig(base, vol, spread);
            } else {
                oldValue.setSpread(spread);
                oldValue.setVolatility(vol);
                return oldValue;
            }
        });

        Currency term = CurrencyMapper.getTerm(pair.value());
        configOverridesByCcy.compute(term, (k, oldValue) -> {
            if (oldValue == null) {
                return new RawPriceConfig(term, vol, spread);
            } else {
                oldValue.setSpread(spread);
                oldValue.setVolatility(vol);
                return oldValue;
            }
        });

        if (pairs.containsKey(pair)) {
            pairs.get(pair).setVolatility(vol);
            pairs.get(pair).setSpread(spread);
        }
    }

    public Set<CurrencyPair> symbols() {
        return pairs.keySet();
    }

    public void generateAll(long now, double dtSeconds) {
        for (Map.Entry<CurrencyPair, PairModel> entry : pairs.entrySet()) {
            PairModel model = entry.getValue();
            MarketDataTick tick = model.nextTick(now, dtSeconds);
            aeronPub.publish(tick);
        }
    }

    public List<RawPriceConfig> generateAllConfig() {
        return new ArrayList<>(configOverridesByCcy.values());
    }

    public MarketDataTick generate(CurrencyPair symbol, long now, double dtSeconds) {
        PairModel model = pairs.get(symbol);
        if (model == null) {
            LOGGER.warn("Unknown symbol requested: {}", symbol);
            throw new IllegalArgumentException("Unknown symbol: " + symbol);
        }
        return model.nextTick(now, dtSeconds);
    }

    @Override
    public int doWork() {
        generateAll(cachedClock.nanoTime(), throttle.getDtSeconds());
        return 1;
    }

    @Override
    public String roleName() {
        return "";
    }

    private static class PairModel {
        final CurrencyPair symbol;
        double price, volatility, spread;

        PairModel(CurrencyPair symbol, double price, double vol, double spr) {
            this.symbol = symbol;
            this.price = price;
            this.volatility = vol;
            this.spread = spr;
        }

        public CurrencyPair getSymbol() {
            return symbol;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public double getVolatility() {
            return volatility;
        }

        public void setVolatility(double volatility) {
            this.volatility = volatility;
        }

        public double getSpread() {
            return spread;
        }

        public void setSpread(double spread) {
            this.spread = spread;
        }

        MarketDataTick nextTick(long now, double dt) {
            double z = ThreadLocalRandom.current().nextGaussian();
            price *= Math.exp(-0.5 * volatility * volatility * dt + volatility * Math.sqrt(dt) * z);
            double spread = price * this.spread / 10000;
            return new MarketDataTick(symbol,
                    price,
                    Math.abs(price - spread) * 0.5,
                    Math.abs(price + spread) * 0.5,
                    HolidayCalendar.getValueDate(),
                    now
            );
        }
    }
}
