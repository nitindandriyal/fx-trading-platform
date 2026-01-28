package pub.lab.trading.common.lifecycle;

import net.openhft.affinity.AffinityLock;
import org.agrona.concurrent.AgentRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentAffinityLocker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentAffinityLocker.class);

    private AgentAffinityLocker() {
        // utility class
    }

    public static void pin(final AgentRunner agentRunner) {
        pin(agentRunner, -1);
    }

    public static void pin(final AgentRunner agentRunner, final int cpuId) {
        boolean acquired = false;
        try (final AffinityLock lock = getAffinityLock(cpuId)) {
            acquired = lock.isAllocated();
            if (acquired) {
                LOGGER.info("Successfully bound to CPU: {} ", lock.cpuId());
            } else {
                LOGGER.info("CPU {} is not available, will continue", cpuId);
            }
            // Start the AgentRunner
            agentRunner.run();
        } finally {
            if (acquired) {
                LOGGER.info("Releasing cpu lock/reservation from CPU {}", cpuId);
            }
        }
    }

    private static AffinityLock getAffinityLock(int cpuId) {
        return cpuId == -1 ? AffinityLock.acquireLock() : AffinityLock.acquireLock(cpuId);
    }
}
