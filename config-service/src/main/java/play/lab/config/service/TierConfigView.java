package play.lab.config.service;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pub.lab.trading.common.model.config.ClientTierFlyweight;

import java.util.Optional;
import java.util.Set;

public class TierConfigView extends VerticalLayout {
    private static final Logger LOGGER = LoggerFactory.getLogger(TierConfigView.class);

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

    private final AeronService aeronService;

    public TierConfigView(AeronService aeronService) {
        this.aeronService = aeronService;
        setSizeFull();
        initializeComponents();
    }

    public void init() {
        try {
            Set<ClientTierFlyweight> tiers = aeronService.getCachedTiers();
            dataProvider.getItems().clear();
            dataProvider.getItems().addAll(tiers);
            dataProvider.refreshAll();
            Notification.show("Loaded " + tiers.size() + " tiers from archive");
            LOGGER.info("Loaded {} tiers from AeronService", tiers.size());
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
            String tierName = tierNameField.getValue();
            int tierId = tierIdField.getValue().intValue();
            double markupBps = markupBpsField.getValue();
            double spreadTighteningFactor = spreadTighteningFactorField.getValue();
            long quoteThrottleMs = quoteThrottleMsField.getValue().longValue();
            long latencyProtectionMs = latencyProtectionMsField.getValue().longValue();
            long quoteExpiryMs = quoteExpiryMsField.getValue().longValue();
            double minNotional = minNotionalField.getValue();
            double maxNotional = maxNotionalField.getValue();
            byte pricePrecision = pricePrecisionField.getValue().byteValue();
            boolean streamingEnabled = streamingEnabledField.getValue();
            boolean limitOrderEnabled = limitOrderEnabledField.getValue();
            boolean accessToCrosses = accessToCrossesField.getValue();
            double creditLimitUsd = creditLimitUsdField.getValue();
            byte tierPriority = tierPriorityField.getValue().byteValue();

            // OPTIMISTIC UPDATE: Create tier object and add to grid immediately
            // This provides instant UI feedback while Aeron syncs in the background
            try {
                ClientTierFlyweight optimisticTier = new ClientTierFlyweight();
                org.agrona.concurrent.UnsafeBuffer buffer = new org.agrona.concurrent.UnsafeBuffer(
                    java.nio.ByteBuffer.allocateDirect(1024));

                optimisticTier.wrap(buffer, 0)
                        .initMessage()
                        .setTierId(tierId)
                        .setTierName(tierName)
                        .setMarkupBps(markupBps)
                        .setSpreadTighteningFactor(spreadTighteningFactor)
                        .setQuoteThrottleMs(quoteThrottleMs)
                        .setLatencyProtectionMs(latencyProtectionMs)
                        .setQuoteExpiryMs(quoteExpiryMs)
                        .setMinNotional(minNotional)
                        .setMaxNotional(maxNotional)
                        .setPricePrecision(pricePrecision)
                        .setStreamingEnabled(streamingEnabled)
                        .setLimitOrderEnabled(limitOrderEnabled)
                        .setAccessToCrosses(accessToCrosses)
                        .setCreditLimitUsd(creditLimitUsd)
                        .setTierPriority(tierPriority);

                // Add to grid immediately (optimistic)
                if (dataProvider != null) {
                    dataProvider.getItems().add(optimisticTier);
                    dataProvider.refreshAll();
                    LOGGER.info("✅ Tier added to grid (optimistic): tierId={}, tierName={}", tierId, tierName);
                } else {
                    LOGGER.warn("⚠️ DataProvider is null, cannot add tier optimistically");
                }
            } catch (Exception e) {
                LOGGER.warn("⚠️ Could not create optimistic tier, will update when confirmed: {}", e.getMessage());
            }

            // Clear form immediately
            clearForm();

            // Send async confirmation to server via Aeron
            aeronService.sendTier(
                    tierId,
                    tierName,
                    markupBps,
                    spreadTighteningFactor,
                    quoteThrottleMs,
                    latencyProtectionMs,
                    quoteExpiryMs,
                    minNotional,
                    maxNotional,
                    pricePrecision,
                    streamingEnabled,
                    limitOrderEnabled,
                    accessToCrosses,
                    creditLimitUsd,
                    tierPriority,
                    this,
                    getUI()
            );

            // Show user feedback
            Notification.show("✅ Tier '" + tierName + "' added. Syncing with server...", 2000, Notification.Position.BOTTOM_CENTER);
            LOGGER.info("Tier added (optimistic): tierId={}, tierName={}", tierId, tierName);

        } catch (IllegalArgumentException e) {
            Notification.show("Invalid input: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
            LOGGER.error("Invalid argument when adding tier", e);
        } catch (Exception e) {
            Notification.show("Error adding tier: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
            LOGGER.error("Error adding tier", e);
        }
    }

    public void refreshGrid(Optional<UI> ui) {
        LOGGER.info("🔄 Refreshing grid with latest tiers from AeronService. UI present: {}", ui.isPresent());
        ui.ifPresentOrElse(
            u -> u.access(() -> {
                LOGGER.info("✅ Inside UI access block, refreshing grid with latest tiers");
                try {
                    if (aeronService != null) {
                        Set<ClientTierFlyweight> tiers = aeronService.getCachedTiers();
                        LOGGER.info("📊 Fetched {} tiers from AeronService cache", tiers.size());

                        if (dataProvider != null) {
                            dataProvider.getItems().clear();
                            dataProvider.getItems().addAll(tiers);
                            dataProvider.refreshAll();
                            LOGGER.info("✅ Grid refreshed with {} tiers", tiers.size());
                        } else {
                            LOGGER.warn("⚠️ DataProvider is null, cannot refresh grid");
                        }
                    } else {
                        LOGGER.warn("⚠️ AeronService is null, cannot refresh grid");
                    }
                } catch (Exception e) {
                    LOGGER.error("❌ Error refreshing grid", e);
                }
            }),
            () -> LOGGER.warn("⚠️ UI not present, cannot refresh grid")
        );
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
