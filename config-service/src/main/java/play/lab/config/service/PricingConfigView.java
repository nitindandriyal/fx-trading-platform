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
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.component.DetachEvent;
import play.lab.config.service.components.EditableConfigRow;
import play.lab.marketdata.model.MarketDataTick;

import java.util.ArrayList;

public class PricingConfigView extends VerticalLayout implements PriceUpdateListener {
    private final Grid<MarketDataTick> grid = new Grid<>(MarketDataTick.class);
    private final Grid<EditableConfigRow> configGrid = new Grid<>(EditableConfigRow.class);
    private final TextField symbolField = new TextField("Symbol (e.g. EURUSD)");
    private final NumberField priceField = new NumberField("Initial Price");
    private final NumberField volField = new NumberField("Volatility");
    private final NumberField spreadField = new NumberField("Spread (bps)");
    private final AeronService aeronService;
    private ListDataProvider<MarketDataTick> gridDataProvider;

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

        // Initialize grid data provider with empty list
        gridDataProvider = new ListDataProvider<>(new ArrayList<>(aeronService.getPrices()));
        grid.setDataProvider(gridDataProvider);

        // Subscribe to price updates (event-driven instead of polling)
        aeronService.subscribePriceUpdates(this);

        // Initial load
        refreshGrid();
    }

    @Override
    public void onPriceUpdate(MarketDataTick tick) {
        // Called for every tick (30+ Hz)
        getUI().ifPresent(ui -> ui.access(() -> {
            if(gridDataProvider != null && tick != null) {
                // Check if this pair already exists in grid
                boolean found = false;
                for (MarketDataTick existing : gridDataProvider.getItems()) {
                    if (existing.getPair().equals(tick.getPair())) {
                        // Update existing item
                        gridDataProvider.refreshItem(tick);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    // Add new item
                    gridDataProvider.getItems().add(tick);
                    gridDataProvider.refreshAll();
                }
            }
        }));
    }

    private void refreshGrid() {
        getUI().ifPresent(ui -> ui.access(() -> {
            if(aeronService != null && gridDataProvider != null) {
                gridDataProvider.getItems().clear();
                gridDataProvider.getItems().addAll(aeronService.getPrices());
                gridDataProvider.refreshAll();
            }
        }));
    }

    @Override
    protected void onDetach(DetachEvent event) {
        // Unsubscribe when view is destroyed to prevent memory leaks
        if(aeronService != null) {
            aeronService.unsubscribePriceUpdates(this);
        }
        super.onDetach(event);
    }
}
