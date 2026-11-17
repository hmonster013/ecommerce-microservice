package org.de013.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.notificationservice.service.PaymentEmailWorkers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmailConfigContextTest {
    @Test
    void customSenderStillBindsMailPropertiesForPaymentWorker() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(EmailConfig.class, PaymentEmailWorkers.class)
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class))
                .withBean("paymentEmailDltTemplate", KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("notification.payment-email.events.enabled=true",
                        "spring.mail.host=mail.example.test", "spring.mail.port=2525",
                        "spring.mail.username=test-user", "spring.mail.properties.mail.smtp.auth=false",
                        "spring.mail.properties.mail.smtp.starttls.enable=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PaymentEmailWorkers.class).hasSingleBean(JavaMailSender.class);
                    MailProperties mail = context.getBean(MailProperties.class);
                    assertThat(mail.getHost()).isEqualTo("mail.example.test");
                    assertThat(mail.getPort()).isEqualTo(2525);
                    assertThat(mail.getUsername()).isEqualTo("test-user");
                    assertThat(mail.getProperties()).containsEntry("mail.smtp.auth", "false")
                            .containsEntry("mail.smtp.starttls.enable", "false");
                    JavaMailSenderImpl sender = (JavaMailSenderImpl) context.getBean(JavaMailSender.class);
                    assertThat(sender.getHost()).isEqualTo("mail.example.test");
                    assertThat(sender.getPort()).isEqualTo(2525);
                    assertThat(sender.getUsername()).isEqualTo("test-user");
                    assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", false)
                            .containsEntry("mail.smtp.starttls.enable", false);
                });
    }
}
