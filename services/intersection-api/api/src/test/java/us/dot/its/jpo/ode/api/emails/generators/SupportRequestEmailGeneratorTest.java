package us.dot.its.jpo.ode.api.emails.generators;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import us.dot.its.jpo.ode.api.models.emails.contents.SupportRequestEmailContents;

@ExtendWith(MockitoExtension.class)
class SupportRequestEmailGeneratorTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private UnsubscribeTokenGenerator unsubscribeTokenGenerator;

    @Mock
    private EmailProperties emailProperties;

    private SupportRequestEmailGenerator generator;

    @BeforeEach
    void setUp() {
        when(emailProperties.getCvmgrFrontEndUri()).thenReturn("https://cvmanager.com");
        
        generator = new SupportRequestEmailGenerator(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Test
    void testGenerateEmailBody_Success() {
        // Arrange
        String subject = "Support Request: Login Issue";
        String email = "user@example.com";
        String message = "I cannot log in to the system. Please help.";
        
        SupportRequestEmailContents contents = new SupportRequestEmailContents(email, subject, message);

        String expectedHtml = "<html><body>Support Request</body></html>";
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
        String subject = "Support Request";
        String email = "user@example.com";
        String message = "Help needed";
        
        SupportRequestEmailContents contents = new SupportRequestEmailContents(email, subject, message);

        when(templateEngine.process(eq("emails/announcement"), any(Context.class)))
                .thenAnswer(invocation -> {
                    Context context = invocation.getArgument(1);
                    
                    // Verify context variables
                    assertEquals("New Support Request in CV Manager", context.getVariable("preview_text"));
                    
                    String content1 = (String) context.getVariable("content_1");
                    assertTrue(content1.contains("New support request from " + email));
                    assertTrue(content1.contains(message));
                    
                    assertEquals("CV-Manager User-Submitted Support Request", context.getVariable("footer_address"));
                    
                    return "<html></html>";
                });

        // Act
        generator.generateEmailBody(contents);

        // Assert - verified in the answer callback above
        verify(templateEngine).process(eq("emails/announcement"), any(Context.class));
    }

    @Test
    void testGenerateEmailBody_WithHtmlInMessage() {
        // Arrange
        SupportRequestEmailContents contents = new SupportRequestEmailContents("user@example.com", "Support Request", "<script>alert('xss')</script>Help me please");

        when(templateEngine.process(eq("emails/announcement"), any(Context.class)))
                .thenReturn("<html></html>");

        // Act
        EmailContent result = generator.generateEmailBody(contents);

        // Assert
        assertNotNull(result);
        verify(templateEngine).process(eq("emails/announcement"), any(Context.class));
    }

    @Test
    void testGenerateEmailBody_WithLongMessage() {
        // Arrange
        String longMessage = "This is a very long support request message. ".repeat(100);

        SupportRequestEmailContents contents = new SupportRequestEmailContents("user@example.com", "Support Request", longMessage);

        when(templateEngine.process(eq("emails/announcement"), any(Context.class)))
                .thenReturn("<html></html>");

        // Act
        EmailContent result = generator.generateEmailBody(contents);

        // Assert
        assertNotNull(result);
        verify(templateEngine).process(eq("emails/announcement"), any(Context.class));
    }
}