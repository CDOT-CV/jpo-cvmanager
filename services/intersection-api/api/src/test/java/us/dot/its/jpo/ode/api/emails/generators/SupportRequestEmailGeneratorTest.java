package us.dot.its.jpo.ode.api.emails.generators;

import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.TemplateEngine;

import us.dot.its.jpo.ode.api.SnapshotTestUtils;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.emails.UnsubscribeTokenGenerator;
import us.dot.its.jpo.ode.api.models.emails.EmailContent;
import us.dot.its.jpo.ode.api.models.emails.contents.SupportRequestEmailContents;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
class SupportRequestEmailGeneratorTest {

    @Autowired
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