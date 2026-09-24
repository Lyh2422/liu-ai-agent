package com.lyh.liuaiagent.conversation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ConversationRetentionServiceTest {
    @Test void deletesAllExpiredBatchesUsingConfiguredCutoff() {
        var store = mock(ConversationStore.class);
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(30));
        when(store.deleteExpiredBefore(cutoff, 2)).thenReturn(2, 2, 1);

        new ConversationRetentionService(store, 30, 2, Clock.fixed(now, ZoneOffset.UTC))
                .cleanupExpiredConversations();

        verify(store, times(3)).deleteExpiredBefore(cutoff, 2);
    }

    @Test void nonPositiveRetentionDisablesCleanupAndInvalidBatchIsRejected() {
        var store = mock(ConversationStore.class);
        new ConversationRetentionService(store, 0, 10, Clock.systemUTC()).cleanupExpiredConversations();
        verifyNoInteractions(store);
        assertThrows(IllegalArgumentException.class,
                () -> new ConversationRetentionService(store, 30, 0, Clock.systemUTC()));
    }
}
