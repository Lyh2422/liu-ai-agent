package com.lyh.liuaiagent.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** 定期清除超过保留期的会话；保留天数小于 1 时关闭自动清理。 */
@Service
@Slf4j
public class ConversationRetentionService {
    private final ConversationStore store;
    private final int retentionDays;
    private final int batchSize;
    private final Clock clock;

    @Autowired
    public ConversationRetentionService(
            ConversationStore store,
            @Value("${app.conversation.retention-days:365}") int retentionDays,
            @Value("${app.conversation.retention-batch-size:500}") int batchSize) {
        this(store, retentionDays, batchSize, Clock.systemUTC());
    }

    ConversationRetentionService(ConversationStore store, int retentionDays, int batchSize, Clock clock) {
        if (batchSize < 1) throw new IllegalArgumentException("清理批次必须为正数");
        this.store = store;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.conversation.retention-cleanup-cron:0 15 3 * * *}")
    public void cleanupExpiredConversations() {
        if (retentionDays < 1) return;
        Instant cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        int total = 0;
        int deleted;
        do {
            deleted = store.deleteExpiredBefore(cutoff, batchSize);
            total += deleted;
        } while (deleted == batchSize);
        if (total > 0) log.info("Expired conversation cleanup removed {} conversations", total);
    }
}
