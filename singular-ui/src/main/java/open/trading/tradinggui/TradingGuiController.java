package open.trading.tradinggui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.TilePane;

public class TradingGuiController {
    @FXML
    private Label welcomeText;

    @FXML
    private TilePane tilePane;

    @FXML
    protected void onHelloButtonClick() {
        tilePane = new TilePane();
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}