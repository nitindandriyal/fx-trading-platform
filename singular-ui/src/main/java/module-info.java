module open.trading.tradinggui {
    requires javafx.controls;
    requires javafx.fxml;
        requires javafx.web;

        requires org.controlsfx.controls;
                requires net.synedra.validatorfx;
            requires org.kordamp.ikonli.javafx;
            requires org.kordamp.bootstrapfx.core;
    requires org.slf4j;
    requires org.agrona;
    requires io.aeron.client;
    requires play.lab.model.sbe;
    requires pub.lab.trading.common;
    requires ch.qos.logback.core;

    opens open.trading.tradinggui to javafx.fxml;
    exports open.trading.tradinggui;
    exports open.trading.tradinggui.widget;
    opens open.trading.tradinggui.widget to javafx.fxml;
}