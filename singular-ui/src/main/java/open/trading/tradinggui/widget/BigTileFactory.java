package open.trading.tradinggui.widget;

public class BigTileFactory {
    public static BigTile create(String instrument) throws InterruptedException {
        return new BigTile(instrument);
    }
}
