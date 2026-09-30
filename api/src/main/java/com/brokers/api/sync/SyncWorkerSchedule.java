package com.brokers.api.sync;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "brokers.sync.worker-enabled", havingValue = "true")
class SyncWorkerSchedule {

    private final SyncWorker worker;

    SyncWorkerSchedule(SyncWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${brokers.sync.poll-interval}")
    void poll() {
        worker.runDueJobs();
    }
}
