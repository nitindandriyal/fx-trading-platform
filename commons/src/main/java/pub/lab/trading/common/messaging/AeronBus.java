package pub.lab.trading.common.messaging;

import org.agrona.DirectBuffer;

public interface AeronBus {
    void poll(int fragmentLimit);

    void onMessage(SubscriptionHandler handler);

    boolean publish(DirectBuffer buffer, int offset, int length);

    interface SubscriptionHandler {
        void onFragment(DirectBuffer buffer, int offset, int length);
    }
}
