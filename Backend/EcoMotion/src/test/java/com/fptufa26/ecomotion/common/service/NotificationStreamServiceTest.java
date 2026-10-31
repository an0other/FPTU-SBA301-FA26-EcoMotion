package com.fptufa26.ecomotion.common.service;
import com.fptufa26.ecomotion.common.service.implement.NotificationStreamServiceImpl;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.mockito.Mockito.*;

class NotificationStreamServiceTest {
    @Test void sendsOnlyAfterCommitToEveryConnection() throws Exception {
        var service = new NotificationStreamServiceImpl(mock(UserService.class), null);
        SseEmitter first = mock(SseEmitter.class), second = mock(SseEmitter.class);
        service.registerEmitter(1L, first); service.registerEmitter(1L, second);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.sendEventToUser(1L, "DRIVER_STATUS_CHANGED", "{}");
            verifyNoInteractions(first, second);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(first).send(any(SseEmitter.SseEventBuilder.class));
            verify(second).send(any(SseEmitter.SseEventBuilder.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        service.removeEmitter(1L, first);
        service.sendEventToUser(1L, "DRIVER_STATUS_CHANGED", "{}");
        verify(first, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }
    @Test void rollbackDoesNotNotify() {
        var service = new NotificationStreamServiceImpl(mock(UserService.class), null);
        SseEmitter emitter = mock(SseEmitter.class); service.registerEmitter(1L, emitter);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.sendEventToUser(1L, "DRIVER_STATUS_CHANGED", "{}");
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(1));
            verifyNoInteractions(emitter);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
