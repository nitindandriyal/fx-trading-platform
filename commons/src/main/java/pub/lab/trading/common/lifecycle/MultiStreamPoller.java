package pub.lab.trading.common.lifecycle;

import org.agrona.concurrent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiStreamPoller implements Agent {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiStreamPoller.class);

    private final Worker[] workers;
    private final String roleName;

    public MultiStreamPoller(String roleName, Worker[] workers) {
        this.roleName = roleName;
        this.workers = workers;
    }

    @Override
    public int doWork() {
        int workCount = 0;
        for (Worker worker : workers) {
            try {
                workCount += worker.doWork();
            } catch (Throwable t) {
                LOGGER.error("Worker {} failed with exception", worker, t);
            }
        }
        return workCount;
    }

    @Override
    public void onClose() {
        for (Worker worker : workers) {
            try {
                worker.onClose();
            } catch (Throwable t) {
                LOGGER.error("Worker {} failed to close properly", worker, t);
            }
        }
    }

    @Override
    public String roleName() {
        return roleName;
    }

    @Override
    public String toString() {
        return roleName;
    }
}
