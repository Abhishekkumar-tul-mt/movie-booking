package com.moviebooking.unit;

import com.moviebooking.domain.DeliveryStatus;
import com.moviebooking.domain.Notification;
import com.moviebooking.domain.NotificationType;
import com.moviebooking.notification.NotificationChannel;
import com.moviebooking.notification.NotificationEvent;
import com.moviebooking.notification.NotificationService;
import com.moviebooking.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    private final NotificationEvent event = new NotificationEvent(1L, "a@b.com", 10L,
            NotificationType.BOOKING_CONFIRMATION, "Subject", "Body");

    @Test
    void retriesThenMarksFailedButNeverThrows() throws Exception {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationRepository repo = mock(NotificationRepository.class);
        doThrow(new RuntimeException("smtp down")).when(channel).send(anyString(), anyString(), anyString());
        NotificationService service = new NotificationService(channel, repo);

        assertThatCode(() -> service.deliver(event)).doesNotThrowAnyException();

        verify(channel, times(3)).send("a@b.com", "Subject", "Body");
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(captor.getValue().getAttempts()).isEqualTo(3);
    }

    @Test
    void recordsSuccessfulDeliveryOnFirstAttempt() throws Exception {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationRepository repo = mock(NotificationRepository.class);
        doNothing().when(channel).send(anyString(), anyString(), anyString());
        NotificationService service = new NotificationService(channel, repo);

        service.deliver(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
    }
}
