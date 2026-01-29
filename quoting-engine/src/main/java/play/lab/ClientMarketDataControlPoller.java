package play.lab;

import io.aeron.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.lab.model.sbe.ClientMarketDataStreamStartRequestDecoder;
import play.lab.model.sbe.ClientMarketDataStreamStopRequestDecoder;
import play.lab.model.sbe.MessageHeaderDecoder;
import pub.lab.trading.common.config.AppId;
import pub.lab.trading.common.lifecycle.Worker;

public class ClientMarketDataControlPoller implements Worker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientMarketDataControlPoller.class);

    private final AppId appId;
    private final Subscription clientControlSubscription;

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ClientMarketDataStreamStartRequestDecoder streamStartRequestDecoder = new ClientMarketDataStreamStartRequestDecoder();
    private final ClientMarketDataStreamStopRequestDecoder streamStopRequestDecoder = new ClientMarketDataStreamStopRequestDecoder();

    public ClientMarketDataControlPoller(final AppId appId, final Subscription clientControlSubscription) {
        this.clientControlSubscription = clientControlSubscription;
        this.appId = appId;
    }

    @Override
    public int doWork() {
        int q = clientControlSubscription.poll((buf, offset, len, hdr) -> {
            messageHeaderDecoder.wrap(buf, offset);
            switch (messageHeaderDecoder.templateId()) {
                case ClientMarketDataStreamStartRequestDecoder.TEMPLATE_ID -> {
                    streamStartRequestDecoder.wrap(buf, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                            messageHeaderDecoder.blockLength(),
                            messageHeaderDecoder.version());
                    LOGGER.info("Received ClientMarketDataStreamStartRequest :: {}", streamStartRequestDecoder);
                    // Handle the start request (e.g., initiate data streams)
                }

                case ClientMarketDataStreamStopRequestDecoder.TEMPLATE_ID -> {
                    streamStopRequestDecoder.wrap(buf, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                            messageHeaderDecoder.blockLength(),
                            messageHeaderDecoder.version());
                    LOGGER.info("Received ClientMarketDataStreamStopRequest :: {}", streamStopRequestDecoder);
                    // Handle the stop request (e.g., terminate data streams)
                }
            }
        }, 10);
        return q;
    }

    @Override
    public String roleName() {
        return appId + "-client-market-data-control-poller";
    }
}

