package pub.lab.trading.common.config.caches;

import play.lab.model.sbe.CurrencyPair;

import java.util.Objects;

public class CurrencyConfig {
    private long id;
    private volatile CurrencyPair symbol;
    private volatile int spotPrecision;
    private volatile int forwardPrecision;
    private volatile int amountPrecision;

    public CurrencyConfig init(long id, CurrencyPair symbol, int spotPrecision, int forwardPrecision, int amountPrecision) {
        Objects.requireNonNull(symbol, "Symbol must not be null");
        if (spotPrecision < 0 || forwardPrecision < 0 || amountPrecision < 0) {
            throw new IllegalArgumentException("Precisions must be non-negative");
        }
        this.id = id;
        this.symbol = symbol;
        this.spotPrecision = spotPrecision;
        this.forwardPrecision = forwardPrecision;
        this.amountPrecision = amountPrecision;
        return this;
    }

    public CurrencyConfig update(CurrencyPair symbol, int spotPrecision, int forwardPrecision, int amountPrecision) {
        Objects.requireNonNull(symbol, "Symbol must not be null");
        if (spotPrecision < 0 || forwardPrecision < 0 || amountPrecision < 0) {
            throw new IllegalArgumentException("Precisions must be non-negative");
        }
        this.symbol = symbol;
        this.spotPrecision = spotPrecision;
        this.forwardPrecision = forwardPrecision;
        this.amountPrecision = amountPrecision;
        return this;
    }

    public long id() {
        return id;
    }

    public CurrencyPair symbol() {
        return symbol;
    }

    public int spotPrecision() {
        return spotPrecision;
    }

    public int forwardPrecision() {
        return forwardPrecision;
    }

    public int amountPrecision() {
        return amountPrecision;
    }

    @Override
    public String toString() {
        return "CurrencyConfig[id=" + id
                + ", symbol=" + symbol
                + ", spotPrecision=" + spotPrecision
                + ", forwardPrecision=" + forwardPrecision
                + ", amountPrecision=" + amountPrecision
                + "]";
    }
}
