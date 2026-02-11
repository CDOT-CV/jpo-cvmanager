package us.dot.its.jpo.ode.api.emails.generators;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.RsuErrorSummaryEmailContents;

@ExtendWith(MockitoExtension.class)
class RsuErrorSummaryEmailGeneratorTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private UnsubscribeTokenGenerator unsubscribeTokenGenerator;

    @Mock
    private EmailProperties emailProperties;

    private RsuErrorSummaryEmailGenerator generator;

    @BeforeEach
    void setUp() {
        when(emailProperties.getCvmgrFrontEndUri()).thenReturn("https://cvmanager.com");
        
        generator = new RsuErrorSummaryEmailGenerator(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Test
    void testGenerateEmailBody_Success() {
        // Arrange
        List<String> recipients = List.of("test@gmail.com");
        String subject = "RSU Error Summary - 5 Errors Detected";
        String message = "<p>Summary of RSU errors:<br>- RSU 192.168.1.1: Connection timeout<br>- RSU 192.168.1.2: Authentication failed</p>";
        
        RsuErrorSummaryEmailContents contents = new RsuErrorSummaryEmailContents(recipients, subject, message);

        String expectedHtml = "<html><body>RSU Error Summary</body></html>";
        when(templateEngine.process(eq("emails/announcement"), any(Context.class)))
                .thenReturn(expectedHtml);

        // Act
        EmailContent result = generator.generateEmailBody(contents);

        // Assert
        assertNotNull(result);
        assertEquals(subject, result.getSubject());
        assertEquals(expectedHtml, result.getBody());
        
        verify(templateEngine).process(eq("emails/announcement"), any(Context.class));
    }

    @Test
    void testGenerateEmailBody_ContextVariablesSet() {
        // Arrange
        List<String> recipients = List.of("test@gmail.com");
        String subject = "RSU Error Summary";
        String message = "Error details here";
        
        RsuErrorSummaryEmailContents contents = new RsuErrorSummaryEmailContents(recipients, subject, message);

        when(templateEngine.process(eq("emails/announcement"), any(Context.class)))
                .thenAnswer(invocation -> {
                    Context context = invocation.getArgument(1);
                    
                    // Verify context variables
                    assertEquals("RSU Error Summary from CV Manager", context.getVariable("preview_text"));
                    assertEquals(message, context.getVariable("content_1"));
                    assertEquals("RSU Error Summary", context.getVariable("footer_address"));
                    assertEquals("This email was sent to you on request of a CV-Manager user. ", 
                                context.getVariable("unsubscribe_pre_text"));
                    assertEquals("", context.getVariable("unsubscribe_link_text"));
                    assertEquals("", context.getVariable("unsubscribe_href"));
                    
                    return "<html></html>";
                });

        // Act
        generator.generateEmailBody(contents);

        // Assert - verified in the answer callback above
        verify(templateEngine).process(eq("emails/announcement"), any(Context.class));
    }

    @Test
    void testGenerateEmailBody_NoUnsubscribeLink() {
        // Arrange
        List<String> recipients = List.of("test@gmail.com");
        String subject = "RSU Error Summary";
        String message = "Error details here";
        RsuErrorSummaryEmailContents contents = new RsuErrorSummaryEmailContents(recipients, subject, message);

        when(templateEngine.process(eq("emails/announcement"), any(Context.class)))
                .thenAnswer(invocation -> {
                    Context context = invocation.getArgument(1);
                    
                    // Verify unsubscribe link is empty
                    assertEquals("", context.getVariable("unsubscribe_link_text"));
                    assertEquals("", context.getVariable("unsubscribe_href"));
                    
                    return "<html></html>";
                });

        // Act
        generator.generateEmailBody(contents);

        // Assert
        verify(templateEngine).process(eq("emails/announcement"), any(Context.class));
    }
}