package com.fptufa26.ecomotion.notification.internal.service;
import com.fptufa26.ecomotion.notification.internal.service.impl.NotificationStreamServiceImpl;
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
    @Test void failedEmitterIsRemovedWithoutAffectingOtherUsers() throws Exception {
        var service = new NotificationStreamServiceImpl(mock(UserService.class), null);
        SseEmitter failed = mock(SseEmitter.class), other = mock(SseEmitter.class);
        doThrow(new java.io.IOException("disconnected")).when(failed).send(any(SseEmitter.SseEventBuilder.class));
        service.registerEmitter(1L, failed);
        service.registerEmitter(2L, other);
        service.sendEventToUser(1L, "UPDATE", "{}");
        service.sendEventToUser(1L, "UPDATE", "{}");
        verify(failed, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(failed).complete();
        verifyNoInteractions(other);
        service.sendEventToUser(2L, "UPDATE", "{}");
        verify(other).send(any(SseEmitter.SseEventBuilder.class));
    }
}
