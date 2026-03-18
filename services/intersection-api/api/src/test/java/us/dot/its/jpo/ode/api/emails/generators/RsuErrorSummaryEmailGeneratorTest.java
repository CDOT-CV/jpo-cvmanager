package us.dot.its.jpo.ode.api.emails.generators;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;

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
import us.dot.its.jpo.ode.api.models.emails.contents.RsuErrorSummaryEmailContents;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
class RsuErrorSummaryEmailGeneratorTest {

    @Autowired
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
    void testGenerateEmailBody_SnapshotTest() throws IOException {
        List<String> recipients = List.of("admin@example.com");
        String subject = "Support Request: System Issues";
        String message = """
                Summary of RSU errors:<br>- RSU 192.168.1.1: Connection timeout<br>- RSU 192.168.1.2: Authentication failed

                And a message with 'quotes', \"double quotes\", and <html> tags & ampersands
                """;

        RsuErrorSummaryEmailContents contents = new RsuErrorSummaryEmailContents(recipients, subject, message);

        EmailContent result = generator.generateEmailBody(contents);

        String snapshotPath = "emails/rsu_error_summary_email_snapshot.html";
        SnapshotTestUtils.assertMatchesSnapshot(result.getBody(), snapshotPath);
    }
}