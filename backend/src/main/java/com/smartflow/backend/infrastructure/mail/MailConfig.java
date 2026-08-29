package com.smartflow.backend.infrastructure.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * §6.8 - "Envoi d'e-mails pour les événements importants". Property names follow
 * smartflow.mail.* (SMARTFLOW_MAIL_HOST, already anticipated by docker-compose.yml's `api`
 * service), not Spring Boot's own spring.mail.* autoconfiguration - a single explicit bean
 * here keeps that one existing naming as the source of truth instead of introducing a
 * second, unused one.
 */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(@Value("${smartflow.mail.host}") String host,
                                          @Value("${smartflow.mail.port}") int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        return sender;
    }
}
