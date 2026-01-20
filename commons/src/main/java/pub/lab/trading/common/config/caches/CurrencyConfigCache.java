package pub.lab.trading.common.config.caches;

import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.model.sbe.CurrencyPairConfigMessageDecoder;
import pub.lab.trading.common.lifecycle.ArrayObjectPool;

public class CurrencyConfigCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrencyConfigCache.class);

    private final Long2ObjectHashMap<CurrencyConfig> currencyCache = new Long2ObjectHashMap<>();
    private final ArrayObjectPool<CurrencyConfig> currencyConfigArrayObjectPool = new ArrayObjectPool<>("currencyConfigArrayObjectPool", CurrencyConfig::new);

    public CurrencyConfig get(int tierId) {
        return currencyCache.get(tierId);
    }

    public void update(final CurrencyPairConfigMessageDecoder currencyDecoder) {
        if (currencyCache.containsKey(currencyDecoder.id())) {
            CurrencyConfig config = currencyCache.get(currencyDecoder.id()).update(
                    currencyDecoder.symbol(),
                    currencyDecoder.spotPrecision(),
                    currencyDecoder.forwardPrecision(),
                    currencyDecoder.amountPrecision()
            );
            LOGGER.debug("Updated currencyCache :: {}", config);
        } else {
            CurrencyConfig config = currencyConfigArrayObjectPool.get().init(currencyDecoder.id(),
                    currencyDecoder.symbol(),
                    currencyDecoder.spotPrecision(),
                    currencyDecoder.forwardPrecision(),
                    currencyDecoder.amountPrecision()
            );
            currencyCache.put(config.id(), config);
            LOGGER.debug("Added currencyCache :: {}", config);
        }
    }
}
