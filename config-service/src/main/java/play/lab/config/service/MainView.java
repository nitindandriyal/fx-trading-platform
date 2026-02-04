package play.lab.config.service;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;

import java.util.HashMap;
import java.util.Map;

@Route("")
@CssImport("./styles/custom-tabs-dark.css")
public class MainView extends VerticalLayout implements RouterLayout, BeforeEnterObserver {
    private static AeronService aeronServiceStatic;

    public MainView(AeronService aeronService) {
        aeronServiceStatic = aeronService;
        RouteTabs routeTabs = new RouteTabs();
        routeTabs.add(new RouterLink("Tier Config", TiersView.class));
        routeTabs.add(new RouterLink("Pricing Config", PricingView.class, "txt"));
        add(routeTabs);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getNavigationTarget() == MainView.class) {
            event.forwardTo(TiersView.class);
        }
    }

    @Route(value = "tiers", layout = MainView.class)
    public static class TiersView extends Div {
        public TiersView() {
            TierConfigView tierConfigView = new TierConfigView(aeronServiceStatic);
            tierConfigView.init();
            add(tierConfigView);
        }
    }

    @Route(value = "pricing", layout = MainView.class)
    public static class PricingView extends Div implements HasUrlParameter<String> {
        @Override
        public void setParameter(BeforeEvent beforeEvent, String s) {
            PricingConfigView pricingConfigView = new PricingConfigView(aeronServiceStatic);
            pricingConfigView.init();
            add(pricingConfigView);
        }
    }

    private static class RouteTabs extends Tabs implements BeforeEnterObserver {
        private final Map<RouterLink, Tab> routerLinkTabMap = new HashMap<>();

        public RouteTabs() {
            // Add custom class for styling
            addClassName("custom-tabs");

            // Minimal inline styles (most moved to CSS)
            getStyle()
                    .set("padding", "0")
                    .set("margin", "0");

            // Style individual tabs
            addAttachListener(event -> {
                getChildren().forEach(component -> {
                    if (component instanceof Tab) {
                        component.addClassName("custom-tab");
                        component.getElement().getStyle()
                                .set("position", "relative") // For pseudo-elements
                                .set("z-index", "1"); // Ensure tabs are above container
                    }
                });
            });

            // Style selected tab
            addSelectedChangeListener(event -> {
                routerLinkTabMap.values().forEach(tab -> {
                    if (tab.equals(getSelectedTab())) {
                        tab.addClassName("selected-tab");
                        tab.getElement().getStyle().set("z-index", "2"); // Bring active tab forward
                    } else {
                        tab.removeClassName("selected-tab");
                        tab.getElement().getStyle().set("z-index", "1"); // Reset z-index
                    }
                });
            });
        }

        public void add(RouterLink routerLink) {
            routerLink.setHighlightCondition(HighlightConditions.sameLocation());
            routerLink.setHighlightAction(
                    (link, shouldHighlight) -> {
                        if (shouldHighlight) setSelectedTab(routerLinkTabMap.get(routerLink));
                    }
            );
            routerLinkTabMap.put(routerLink, new Tab(routerLink));
            add(routerLinkTabMap.get(routerLink));
        }

        @Override
        public void beforeEnter(BeforeEnterEvent event) {
            setSelectedTab(null); // Reset selection
        }
    }
}