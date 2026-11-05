package com.fptufa26.ecomotion.common.service;

import com.fptufa26.ecomotion.common.service.implement.NotificationStreamServiceImpl;
import com.fptufa26.ecomotion.user.service.UserService;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class NotificationStreamFailureTest {
    private final NotificationStreamServiceImpl service = new NotificationStreamServiceImpl(mock(UserService.class), null);

    static class Connection extends SseEmitter {
        int sends;
        boolean broken;
        @Override public void send(SseEventBuilder event) throws IOException {
            sends++;
            if (broken) throw new IllegalStateException("AsyncContext already failed");
        }
        @Override public void complete() { throw new IllegalStateException("Already completed"); }
    }

    @Test void failedSendAndCloseCannotEscapeOrBlockHealthyConnections() {
        Connection broken = new Connection(); broken.broken = true;
        Connection healthy = new Connection();
        service.registerEmitter(1L, broken); service.registerEmitter(1L, healthy);
        assertThatCode(() -> service.sendEventToUser(1L, "BOOKING_CHANGED", Map.of("status", "IN_PROGRESS")))
                .doesNotThrowAnyException();
        service.sendEventToUser(1L, "BOOKING_CHANGED", Map.of());
        assertThat(broken.sends).isEqualTo(1);
        assertThat(healthy.sends).isEqualTo(2);
    }

    @Test void failingConnectionAfterCommitCannotTurnCommittedOperationIntoFailure() {
        Connection broken = new Connection(); broken.broken = true;
        service.registerEmitter(1L, broken);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.sendEventToUser(1L, "BOOKING_CHANGED", Map.of());
            assertThat(broken.sends).isZero();
            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test void rollbackNeverDeliversNotification() {
        Connection connection = new Connection(); service.registerEmitter(1L, connection);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.sendEventToUser(1L, "BOOKING_CHANGED", Map.of());
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(connection.sends).isZero();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
