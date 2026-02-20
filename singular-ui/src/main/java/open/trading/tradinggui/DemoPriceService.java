package open.trading.tradinggui;

import open.trading.tradinggui.config.PairConfig;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class DemoPriceService {
    private static final int LEVELS = 5;

    private final Random r = new Random();
    private final Map<String, Double> mid = new ConcurrentHashMap<>();
    boolean first = true;

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public void start(Map<String, open.trading.tradinggui.widget.BigTile> tiles,
                      Map<String, PairConfig> configs) {

        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {

                for (var e : tiles.entrySet()) {
                    String sym = e.getKey();
                    var tile = e.getValue();
                    var cfg = configs.get(sym);
                    if (cfg == null) continue;

                    double m = mid.compute(sym, (k, v) -> v == null
                            ? (0.90 + r.nextDouble() * 0.20)
                            : clamp(v + (r.nextDouble() - 0.5) * 0.01, 0.50, 1.50));

                    // spread comes from slider
                    double spread = Math.max(0.0001, cfg.getCoreSpreadPips());

                    double topBid = m - spread / 2.0;
                    double topAsk = m + spread / 2.0;

                    double tick = 0.01; // demo ladder tick

                    double[] bidPrices = new double[LEVELS];
                    double[] askPrices = new double[LEVELS];
                    int[] bidSizes = new int[LEVELS];
                    int[] askSizes = new int[LEVELS];

                    int baseQty = 50 + r.nextInt(250);

                    for (int i = 0; i < LEVELS; i++) {
                        bidPrices[i] = topBid - i * tick;
                        askPrices[i] = topAsk + i * tick;

                        int bias = Math.max(0, (LEVELS - 1) - i);
                        int bq = clampInt(((baseQty + r.nextInt(120)) * (1 + bias)) / 2, 1, 5_000_000);
                        int aq = clampInt(((baseQty + r.nextInt(120)) * (1 + bias)) / 2, 1, 5_000_000);

                        if (r.nextBoolean()) bq = clampInt((int) (bq * (1.0 + r.nextDouble() * 0.35)), 1, 50_000);
                        else aq = clampInt((int) (aq * (1.0 + r.nextDouble() * 0.35)), 1, 50_000);

                        bidSizes[i] = bq;
                        askSizes[i] = aq;
                    }

                    if (first) {
                        // init with some data to avoid empty tiles
                        first = false;
                    } else {
                        tile.updateBook(topBid, topAsk, bidPrices, bidSizes, askPrices, askSizes);
                    }
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(120);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "demo-price-service");

        t.setDaemon(true);
        t.start();
    }
}
