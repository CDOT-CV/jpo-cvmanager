package us.dot.its.jpo.ode.api.emails.generators;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Instant;

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
import us.dot.its.jpo.ode.api.models.emails.contents.ApiErrorEmailContents;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
class ApiErrorEmailGeneratorTest {

    @Autowired
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
    void testGenerateEmailBody_SnapshotTest() throws IOException {
        Instant timestamp = Instant.parse("2024-02-11T10:30:00Z");
        String logsLink = "https://cvmanager.com/logs";
        String errorMessage = """
                NullPointerException occurred

                And a message with 'quotes', \"double quotes\", and <html> tags & ampersands
                """;
        String stackTrace = """
                at com.example.Service.method(Service.java:42)
                at com.example.Controller.handle(Controller.java:27)""";

        ApiErrorEmailContents contents = new ApiErrorEmailContents(errorMessage, stackTrace, timestamp, logsLink);

        EmailContent result = generator.generateEmailBody(contents);

        String snapshotPath = "emails/api_error_email_snapshot.html";
        SnapshotTestUtils.assertMatchesSnapshot(result.getBody(), snapshotPath);
    }
}