package open.trading.tradinggui.config;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

public final class PairConfig {
    private final String symbol;

    // spread in PIPS (e.g., 0.8 pips)
    private final DoubleProperty coreSpreadPips = new SimpleDoubleProperty(0.8);

    public PairConfig(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public DoubleProperty coreSpreadPipsProperty() {
        return coreSpreadPips;
    }

    public double getCoreSpreadPips() {
        return coreSpreadPips.get();
    }

    public void setCoreSpreadPips(double v) {
        coreSpreadPips.set(v);
    }
}
