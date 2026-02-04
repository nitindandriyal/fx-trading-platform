package play.lab.config.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import play.lab.config.service.components.EditableConfigRow;
import play.lab.marketdata.model.MarketDataTick;

public class PricingConfigView extends VerticalLayout {
    private final Grid<MarketDataTick> grid = new Grid<>(MarketDataTick.class);
    private final Grid<EditableConfigRow> configGrid = new Grid<>(EditableConfigRow.class);
    private final TextField symbolField = new TextField("Symbol (e.g. EURUSD)");
    private final NumberField priceField = new NumberField("Initial Price");
    private final NumberField volField = new NumberField("Volatility");
    private final NumberField spreadField = new NumberField("Spread (bps)");
    private AeronService aeronService;
    public PricingConfigView(AeronService aeronServiceStatic) {
        this.aeronService = aeronServiceStatic;

        setSizeFull();

        NumberField throttleField = new NumberField("Ticks/sec");
        throttleField.setStep(100);
        add(throttleField, grid);

        symbolField.setPlaceholder("e.g. USDJPY");
        volField.setPlaceholder("e.g. 0.02");
        spreadField.setPlaceholder("e.g. 0.5");
        priceField.setPlaceholder("e.g. 1.1000");

        Button addSymbolButton = getAddSymbolButton();

        HorizontalLayout formLayout = new HorizontalLayout();
        formLayout.add(symbolField, priceField, volField, spreadField, addSymbolButton);
        formLayout.setDefaultVerticalComponentAlignment(Alignment.END);
        add(formLayout);
        Span streamingPrices = new Span("Streaming Prices");
        add(streamingPrices, grid);
        Span config = new Span("Currency Config");
        add(config, configGrid);
    }

    private Button getAddSymbolButton() {
        Button addSymbolButton = new Button("Add Symbol");

        addSymbolButton.addClickListener(e -> {
            String symbol = symbolField.getValue().toUpperCase().trim();
            Double price = priceField.getValue();
            Double vol = volField.getValue();
            Double spread = spreadField.getValue();

            if (symbol.length() != 6 || price == null || vol == null || spread == null) {
                Notification.show("Please fill all fields correctly.");
                return;
            }

            refreshGrid();
            Notification.show("Added: " + symbol);

            // Clear form
            symbolField.clear();
            priceField.clear();
            volField.clear();
            spreadField.clear();
        });
        return addSymbolButton;
    }

    void init() {
        grid.setColumns("pair", "mid", "bid", "ask", "timestamp");
        configGrid.setColumns("ccy", "volatility", "spread");
        // Enable polling every 1 second
        UI.getCurrent().setPollInterval(1000);

        // On each doWork, refresh the grid
        UI.getCurrent().addPollListener(e -> refreshGrid());

        refreshGrid();

    }

    private void refreshGrid() {
        getUI().ifPresent(ui -> ui.access(() -> {
            if(aeronService!=null) {
                grid.setItems(aeronService.getPrices());
            }
        }));
    }

    public void setAeronService(AeronService aeronService) {
        this.aeronService = aeronService;
    }
}
