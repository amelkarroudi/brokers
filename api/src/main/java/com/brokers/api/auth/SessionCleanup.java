package com.brokers.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SessionCleanup {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanup.class);

    private final AuthService authService;

    SessionCleanup(AuthService authService) {
        this.authService = authService;
    }

    @Scheduled(cron = "0 17 * * * *")
    void purgeExpiredSessions() {
        int removed = authService.purgeExpiredSessions();
        if (removed == 0) {
            return;
        }
        log.info("Purged {} expired sessions", removed);
    }
}
