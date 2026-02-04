package play.lab.config.service;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import jakarta.annotation.PostConstruct;
import pub.lab.trading.common.model.config.ClientTierFlyweight;

import java.util.List;

public class TierConfigView extends VerticalLayout {
    private final Grid<ClientTierFlyweight> grid = new Grid<>();
    private final NumberField tierIdField = new NumberField("Tier ID");
    private final TextField tierNameField = new TextField("Tier Name");
    private final NumberField markupBpsField = new NumberField("Markup (bps)");
    private final NumberField spreadTighteningFactorField = new NumberField("Spread Factor");
    private final NumberField quoteThrottleMsField = new NumberField("Quote Throttle (ms)");
    private final NumberField latencyProtectionMsField = new NumberField("Latency Prot. (ms)");
    private final NumberField quoteExpiryMsField = new NumberField("Quote Expiry (ms)");
    private final NumberField minNotionalField = new NumberField("Min Notional");
    private final NumberField maxNotionalField = new NumberField("Max Notional");
    private final NumberField pricePrecisionField = new NumberField("Price Precision");
    private final Checkbox streamingEnabledField = new Checkbox("Streaming Enabled");
    private final Checkbox limitOrderEnabledField = new Checkbox("Limit Order Enabled");
    private final Checkbox accessToCrossesField = new Checkbox("Access to Crosses");
    private final NumberField creditLimitUsdField = new NumberField("Credit Limit (USD)");
    private final NumberField tierPriorityField = new NumberField("Tier Priority");
    private final Button addButton = new Button("Add Tier");
    private ListDataProvider<ClientTierFlyweight> dataProvider;
    private AeronService aeronService;

    public TierConfigView(AeronService aeronService) {
        this.aeronService = aeronService;
        setSizeFull();
        initializeComponents();
    }

    public void init() {
        try {
            List<ClientTierFlyweight> tiers = aeronService.getCachedTiers();
            dataProvider.getItems().clear();
            dataProvider.getItems().addAll(tiers);
            dataProvider.refreshAll();
            Notification.show("Loaded " + tiers.size() + " tiers from archive");
        } catch (Exception e) {
            Notification.show("Error loading tiers: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }

    private void initializeComponents() {
        // Configure grid
        grid.addColumn(ClientTierFlyweight::getTierId).setHeader("Tier ID");
        grid.addColumn(ClientTierFlyweight::getTierNameAsString).setHeader("Tier Name");
        grid.addColumn(ClientTierFlyweight::getMarkupBps).setHeader("Markup (bps)");
        grid.addColumn(ClientTierFlyweight::getSpreadTighteningFactor).setHeader("Spread Factor");
        grid.addColumn(ClientTierFlyweight::getQuoteThrottleMs).setHeader("Quote Throttle (ms)");
        grid.addColumn(ClientTierFlyweight::getLatencyProtectionMs).setHeader("Latency Prot. (ms)");
        grid.addColumn(ClientTierFlyweight::getQuoteExpiryMs).setHeader("Quote Expiry (ms)");
        grid.addColumn(ClientTierFlyweight::getMinNotional).setHeader("Min Notional");
        grid.addColumn(ClientTierFlyweight::getMaxNotional).setHeader("Max Notional");
        grid.addColumn(ClientTierFlyweight::getPricePrecision).setHeader("Price Precision");
        grid.addColumn(ClientTierFlyweight::isStreamingEnabled).setHeader("Streaming Enabled");
        grid.addColumn(ClientTierFlyweight::isLimitOrderEnabled).setHeader("Limit Order Enabled");
        grid.addColumn(ClientTierFlyweight::isAccessToCrosses).setHeader("Access to Crosses");
        grid.addColumn(ClientTierFlyweight::getCreditLimitUsd).setHeader("Credit Limit (USD)");
        grid.addColumn(ClientTierFlyweight::getTierPriority).setHeader("Tier Priority");
        grid.setItems(aeronService.getCachedTiers());
        dataProvider = (ListDataProvider<ClientTierFlyweight>) grid.getDataProvider();

        // Configure form fields
        tierIdField.setMin(0);
        tierIdField.setMax(65535);
        tierIdField.setStep(1);
        tierNameField.setMaxLength(64);
        markupBpsField.setMin(0);
        markupBpsField.setMax(1000);
        spreadTighteningFactorField.setMin(0);
        spreadTighteningFactorField.setMax(10);
        quoteThrottleMsField.setMin(0);
        quoteThrottleMsField.setMax(4294967295L);
        quoteThrottleMsField.setStep(1);
        latencyProtectionMsField.setMin(0);
        latencyProtectionMsField.setMax(4294967295L);
        latencyProtectionMsField.setStep(1);
        quoteExpiryMsField.setMin(0);
        quoteExpiryMsField.setMax(4294967295L);
        quoteExpiryMsField.setStep(1);
        minNotionalField.setMin(0);
        maxNotionalField.setMin(0);
        pricePrecisionField.setMin(0);
        pricePrecisionField.setMax(255);
        pricePrecisionField.setStep(1);
        creditLimitUsdField.setMin(0);
        tierPriorityField.setMin(0);
        tierPriorityField.setMax(255);
        tierPriorityField.setStep(1);

        // Configure add button
        addButton.addClickListener(e -> addTier());

        // Layout
        HorizontalLayout form1 = new HorizontalLayout(tierIdField, tierNameField, markupBpsField);
        HorizontalLayout form2 = new HorizontalLayout(spreadTighteningFactorField, quoteThrottleMsField, latencyProtectionMsField);
        HorizontalLayout form3 = new HorizontalLayout(quoteExpiryMsField, minNotionalField, maxNotionalField);
        HorizontalLayout form4 = new HorizontalLayout(pricePrecisionField, creditLimitUsdField, tierPriorityField);
        HorizontalLayout form5 = new HorizontalLayout(streamingEnabledField, limitOrderEnabledField, accessToCrossesField);
        HorizontalLayout form6 = new HorizontalLayout(addButton);
        add(form1, form2, form3, form4, form5, form6, grid);
        setSizeFull();
    }

    private void addTier() {
        if (tierNameField.getValue().isEmpty()) {
            Notification.show("Tier name is required", 3000, Notification.Position.MIDDLE);
            return;
        }
        if (tierNameField.getValue().length() > 64) {
            Notification.show("Tier name must be 64 characters or less", 3000, Notification.Position.MIDDLE);
            return;
        }
        if (tierIdField.isEmpty()
                || markupBpsField.isEmpty()
                || spreadTighteningFactorField.isEmpty()
                || quoteThrottleMsField.isEmpty()
                || latencyProtectionMsField.isEmpty()
                || quoteExpiryMsField.isEmpty()
                || minNotionalField.isEmpty()
                || maxNotionalField.isEmpty()
                || pricePrecisionField.isEmpty()
                || creditLimitUsdField.isEmpty()
                || tierPriorityField.isEmpty()) {
            Notification.show("All fields are required", 3000, Notification.Position.MIDDLE);
            return;
        }

        try {
            aeronService.sendTier(
                    tierIdField.getValue().intValue(),
                    tierNameField.getValue(),
                    markupBpsField.getValue(),
                    spreadTighteningFactorField.getValue(),
                    quoteThrottleMsField.getValue().longValue(),
                    latencyProtectionMsField.getValue().longValue(),
                    quoteExpiryMsField.getValue().longValue(),
                    minNotionalField.getValue(),
                    maxNotionalField.getValue(),
                    pricePrecisionField.getValue().byteValue(),
                    streamingEnabledField.getValue(),
                    limitOrderEnabledField.getValue(),
                    accessToCrossesField.getValue(),
                    creditLimitUsdField.getValue(),
                    tierPriorityField.getValue().byteValue()
            );
            dataProvider.getItems().clear();
            dataProvider.getItems().addAll(aeronService.getCachedTiers());
            dataProvider.refreshAll();
            clearForm();
            Notification.show("Tier added: " + tierNameField.getValue());
        } catch (IllegalArgumentException e) {
            Notification.show("Invalid input: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
        } catch (Exception e) {
            Notification.show("Error adding tier: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
        }
    }

    private void clearForm() {
        tierIdField.clear();
        tierNameField.clear();
        markupBpsField.clear();
        spreadTighteningFactorField.clear();
        quoteThrottleMsField.clear();
        latencyProtectionMsField.clear();
        quoteExpiryMsField.clear();
        minNotionalField.clear();
        maxNotionalField.clear();
        pricePrecisionField.clear();
        streamingEnabledField.clear();
        limitOrderEnabledField.clear();
        accessToCrossesField.clear();
        creditLimitUsdField.clear();
        tierPriorityField.clear();
    }
}
