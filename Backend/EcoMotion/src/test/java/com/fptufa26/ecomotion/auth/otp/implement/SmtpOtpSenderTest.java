package com.fptufa26.ecomotion.auth.otp.implement;

import com.fptufa26.ecomotion.auth.exception.EmailDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpOtpSenderTest {

    private static final String FROM = "no-reply@ecomotion.vn";

    @Test
    @DisplayName("the OTP is placed in the subject/body and sent to the recipient")
    void sendsCodeInBody() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpOtpSender sender = new SmtpOtpSender(mailSender, FROM);

        sender.send("customer@example.com", "0901234567", "482915");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("customer@example.com");
        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getText()).contains("482915");
        assertThat(message.getSubject()).contains("EcoMotion");
    }

    @Test
    @DisplayName("a MailException is translated into EmailDeliveryException")
    void translatesMailException() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("550 5.1.1 The email account that you tried to reach does not exist"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        SmtpOtpSender sender = new SmtpOtpSender(mailSender, FROM);

        assertThatThrownBy(() -> sender.send("nobody@example.com", "0901234567", "111111"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("nobody@example.com");
    }
}
