package play.lab;

import io.aeron.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pub.lab.trading.common.config.AppId;
import pub.lab.trading.common.lifecycle.Worker;
import pub.lab.trading.common.model.pricing.QuoteView;

public class RawMarketDataPoller implements Worker {
    private static final Logger LOGGER = LoggerFactory.getLogger(RawMarketDataPoller.class);

    private final Subscription quoteSub;
    private final QuoteView quoteView = new QuoteView();
    private final AppId appId;

    public RawMarketDataPoller(final AppId appId, final Subscription quoteSub) {
        this.quoteSub = quoteSub;
        this.appId = appId;
    }

    @Override
    public int doWork() {
        int q = quoteSub.poll((buf, offset, len, hdr) -> {
            quoteView.wrap(buf, offset);
            LOGGER.info("Received quote : {}", quoteView.priceCreationTimestamp());
        }, 10);
        return q;
    }

    @Override
    public String roleName() {
        return appId + "-aeron-ipc-poller";
    }
}

