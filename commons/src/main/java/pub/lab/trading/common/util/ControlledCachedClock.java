package pub.lab.trading.common.util;

public class ControlledCachedClock extends CachedClock {
    private long currentTimeNanos;
    private boolean isRunning = false;

    public ControlledCachedClock() {
        this(0L);
    }

    public ControlledCachedClock(long initialNanos) {
        this.currentTimeNanos = initialNanos;
    }

    @Override
    public long nanoTime() {
        return currentTimeNanos;
    }

    /**
     * Sets the clock to a specific historical timestamp.
     */
    public void setTime(long nanos) {
        this.currentTimeNanos = nanos;
    }

    /**
     * Advances the clock manually by a specific duration.
     * Useful for simulating the passage of time between events.
     */
    public void advance(long durationNanos) {
        if (isRunning) {
            this.currentTimeNanos += durationNanos;
        }
    }

    /**
     * "Starts" the clock, allowing it to be advanced.
     */
    public void start() {
        this.isRunning = true;
    }

    /**
     * "Stops" the clock. While stopped, calls to advance()
     * will not change the time.
     */
    public void stop() {
        this.isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
