package pub.lab.trading.common.util;

import org.agrona.concurrent.NanoClock;

public class CachedClock implements NanoClock {
    @Override
    public long nanoTime() {
        return System.nanoTime();
    }
}
