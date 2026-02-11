package us.dot.its.jpo.ode.api.emails.generators;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

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
import us.dot.its.jpo.ode.api.models.emails.contents.ApiErrorEmailContents;

@ExtendWith(MockitoExtension.class)
class ApiErrorEmailGeneratorTest {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private UnsubscribeTokenGenerator unsubscribeTokenGenerator;

    @Mock
    private EmailProperties emailProperties;

    private ApiErrorEmailGenerator generator;

    @BeforeEach
    void setUp() {
        when(emailProperties.getCvmgrFrontEndUri()).thenReturn("https://cvmanager.com");
        
        generator = new ApiErrorEmailGenerator(templateEngine, unsubscribeTokenGenerator, emailProperties);
    }

    @Test
    void testGenerateEmailBody_Success() {
        // Arrange
        Instant timestamp = Instant.parse("2024-02-11T10:30:00Z");
        String logsLink = "https://cvmanager.com/logs";
        String errorMessage = "NullPointerException occurred";
        String stackTrace = "at com.example.Service.method(Service.java:42)";
        
        ApiErrorEmailContents contents = new ApiErrorEmailContents(errorMessage, stackTrace, timestamp, logsLink);

        String expectedHtml = "<html><body>API Error Email</body></html>";
        when(templateEngine.process(eq("emails/announcement"), any(Context.class)))
                .thenReturn(expectedHtml);

        // Act
        EmailContent result = generator.generateEmailBody(contents);

        // Assert
        assertNotNull(result);
        assertEquals("CV-Manager API Error", result.getSubject());
        assertEquals(expectedHtml, result.getBody());
        
        // Verify template engine was called
        verify(templateEngine).process(eq("emails/announcement"), any(Context.class));
    }

    @Test
    void testGenerateEmailBody_ContextVariablesSet() {
        // Arrange
        ApiErrorEmailContents contents = new ApiErrorEmailContents("Error occurred", "Stack trace here", Instant.parse("2024-02-11T10:30:00Z"), "https://cvmanager.com/logs");

        when(templateEngine.process(eq("emails/announcement"), any(Context.class)))
                .thenAnswer(invocation -> {
                    Context context = invocation.getArgument(1);
                    
                    // Verify context variables
                    assertEquals("CV-Manager API Error", context.getVariable("preview_text"));
                    assertTrue(((String) context.getVariable("content_1")).contains("A critical API Error has occurred"));
                    assertTrue(((String) context.getVariable("content_1")).contains(contents.getTimestamp().toString()));
                    assertTrue(((String) context.getVariable("content_1")).contains(contents.getLogsLink()));
                    assertTrue(((String) context.getVariable("content_1")).contains(contents.getErrorMessage()));
                    assertTrue(((String) context.getVariable("content_1")).contains(contents.getStackTrace()));
                    assertEquals("API Error Notification", context.getVariable("footer_address"));
                    
                    return "<html></html>";
                });

        // Act
        generator.generateEmailBody(contents);

        // Assert - verified in the answer callback above
        verify(templateEngine).process(eq("emails/announcement"), any(Context.class));
    }
}