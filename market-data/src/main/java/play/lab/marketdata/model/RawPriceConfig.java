package play.lab.marketdata.model;

import play.lab.model.sbe.Currency;

public class RawPriceConfig {
    private final Currency ccy;
    private double volatility;
    private double spread;

    public RawPriceConfig(Currency symbol, double vol, double spr) {
        this.ccy = symbol;
        this.volatility = vol;
        this.spread = spr;
    }

    public Currency getCcy() {
        return ccy;
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
}
