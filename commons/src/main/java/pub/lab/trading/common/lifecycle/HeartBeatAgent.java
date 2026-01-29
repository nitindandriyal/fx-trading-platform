package pub.lab.trading.common.lifecycle;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import org.agrona.concurrent.UnsafeBuffer;
import play.lab.model.sbe.HeartbeatMessageDecoder;
import play.lab.model.sbe.MessageHeaderDecoder;
import pub.lab.trading.common.config.AeronConfigs;
import pub.lab.trading.common.config.AppId;
import pub.lab.trading.common.config.StreamId;
import pub.lab.trading.common.model.hb.HeartbeatView;
import pub.lab.trading.common.model.hb.HeartbeatWriter;

import java.nio.ByteBuffer;

public class HeartBeatAgent implements Worker {

    private final HeartbeatWriter hbWriter = new HeartbeatWriter();
    private final HeartbeatView hbView = new HeartbeatView();

    private final AppId appId;
    private final long heartbeatIntervalMs;
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

    private final Subscription heartbeatSub;
    private final Publication heartbeatPub;

    private long lastHbTime = 0;

    public HeartBeatAgent(final AppId appId, final long heartbeatIntervalMs, final Aeron aeron) {
        this.appId = appId;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.heartbeatSub = aeron.addSubscription(AeronConfigs.LIVE_CHANNEL, StreamId.HEARTBEAT.getCode());
        this.heartbeatPub = aeron.addExclusivePublication(AeronConfigs.LIVE_CHANNEL, StreamId.HEARTBEAT.getCode());
    }

    @Override
    public int doWork() throws Exception {
        int h = heartbeatSub.poll((buf, offset, len, hdr) -> {
            hbView.wrap(buf, offset + MessageHeaderDecoder.ENCODED_LENGTH, HeartbeatMessageDecoder.BLOCK_LENGTH, HeartbeatMessageDecoder.SCHEMA_VERSION);
        }, 10);

        long now = System.currentTimeMillis();
        if (now - lastHbTime >= heartbeatIntervalMs) {
            hbWriter.wrap(buffer, 0).appId(appId.getCode()).timestamp(now);
            heartbeatPub.offer(buffer, 0, hbWriter.encodedLength());
            lastHbTime = now;
        }

        return h;
    }

    @Override
    public String roleName() {
        return appId + "-heartbeat";
    }
}
