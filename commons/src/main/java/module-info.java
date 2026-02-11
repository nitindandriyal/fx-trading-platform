module pub.lab.trading.common {
    requires org.agrona;
    requires play.lab.model.sbe;
    requires org.slf4j;
    requires affinity;
    requires io.aeron.client;
    // Add this line to make the package visible to everyone
    exports pub.lab.trading.common.config;
    exports pub.lab.trading.common.lifecycle;
    exports pub.lab.trading.common.model.pricing;
}