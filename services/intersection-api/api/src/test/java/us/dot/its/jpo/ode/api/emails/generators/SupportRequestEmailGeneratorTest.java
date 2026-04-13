package us.dot.its.jpo.ode.api.emails.generators;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import us.dot.its.jpo.ode.api.SnapshotTestUtils;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.SupportRequestEmailContents;

class SupportRequestEmailGeneratorTest {

    @Mock
    private UnsubscribeTokenGenerator unsubscribeTokenGenerator;

    @Mock
    private EmailProperties emailProperties;

    private TemplateEngine templateEngine;
    private SupportRequestEmailGenerator generator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(emailProperties.getCvmgrFrontEndUri()).thenReturn("https://cvmanager.com");

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

        generator = new SupportRequestEmailGenerator(templateEngine, unsubscribeTokenGenerator, emailProperties);

        when(unsubscribeTokenGenerator.generateUnsubscribeUrl(anyString()))
                .thenReturn("https://cvmanager.com/unsubscribe?token=abc123");
    }

    @Test
    void testGenerateEmailBody_SnapshotTest() throws IOException {
        String subject = "Support Request: System Issues";
        String email = "admin@example.com";
        String message = """
                I'm experiencing multiple issues:

                1. Cannot access dashboard
                2. Reports are not loading
                3. Email notifications not working

                Please investigate as soon as possible.

                And a message with 'quotes', \"double quotes\", and <html> tags & ampersands
                """;

        SupportRequestEmailContents contents = new SupportRequestEmailContents(email, subject, message);

        EmailContent result = generator.generateEmailBody(contents);

        String snapshotPath = "emails/support_request_email_multiline_snapshot.html";
        SnapshotTestUtils.assertMatchesSnapshot(result.getBody(), snapshotPath);
    }
}