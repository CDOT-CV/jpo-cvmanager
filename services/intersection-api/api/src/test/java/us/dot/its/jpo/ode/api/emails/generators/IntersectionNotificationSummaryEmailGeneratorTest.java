package us.dot.its.jpo.ode.api.emails.generators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.ConnectionOfTravelNotification;
import us.dot.its.jpo.conflictmonitor.monitor.models.notifications.Notification;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailWrapper;
import us.dot.its.jpo.ode.api.models.emails.contents.IntersectionNotificationSummaryEmailContents;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IntersectionNotificationSummaryEmailGeneratorTest {
        private TemplateEngine templateEngine;

        @Mock
        private UnsubscribeTokenGenerator unsubscribeTokenGenerator;

        @Mock
        private EmailProperties emailProperties;

        private IntersectionNotificationSummaryEmailGenerator generator;

        @BeforeEach
        void setUp() {
                MockitoAnnotations.openMocks(this);

                when(emailProperties.getCvmgrFrontEndUri()).thenReturn("https://cvmanager.example.com");

                // Configure the template resolver
                ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
                templateResolver.setPrefix("templates/"); // Path to your templates directory
                templateResolver.setSuffix(".html"); // Template file extension
                templateResolver.setTemplateMode("HTML");
                templateResolver.setCharacterEncoding("UTF-8");

                // Configure the SpringTemplateEngine
                SpringTemplateEngine springTemplateEngine = new SpringTemplateEngine();
                springTemplateEngine.setTemplateResolver(templateResolver);

                this.templateEngine = springTemplateEngine;

                generator = new IntersectionNotificationSummaryEmailGenerator(templateEngine, unsubscribeTokenGenerator,
                                emailProperties);
        }

        @Test
        void testGenerateEmail() {
                // Arrange
                String emailAddress = "user@example.com";
                String unsubscribeUrl = "https://cvmanager.example.com/unsubscribe?token=abc123";
                Notification notification = new ConnectionOfTravelNotification();
                List<Notification> notifications = Collections.singletonList(notification);

                IntersectionNotificationSummaryEmailContents data = new IntersectionNotificationSummaryEmailContents(
                                notifications);

                when(unsubscribeTokenGenerator.generateUnsubscribeUrl(emailAddress))
                                .thenReturn(unsubscribeUrl);

                // Act
                EmailWrapper emailWrapper = generator.generateEmailBody(emailAddress, data);

                // Assert
                assertEquals(emailAddress, emailWrapper.getRecipientEmail());
                assertEquals("CV-Manager New CM Notifications: " + generator.dateTimeFormatter.format(Instant.now()),
                                emailWrapper.getSubject());
                assertEquals(unsubscribeUrl, emailWrapper.getUnsubscribeUrl());
                assertTrue(emailWrapper.getBody().contains("CV Manager - New Notifications"));
                assertTrue(emailWrapper.getBody().contains(unsubscribeUrl));
        }
}