package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.data.model.message.MessageResponse;
import com.postmarkapp.postmark.client.exception.PostmarkException;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import us.dot.its.jpo.ode.api.emails.EmailProperties;
import us.dot.its.jpo.ode.api.models.emails.EmailBrokerType;

import java.io.IOException;

class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SendGrid sendGrid;

    @Mock
    private ApiClient postmark;

    @Mock
    private EmailProperties props;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(props.getSenderAddress()).thenReturn("test@example.com");
    }

    @Test
    void testSendEmailViaSendGrid() throws IOException {
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        when(sendGrid.api(any(Request.class))).thenReturn(new Response());

        emailService.sendEmailViaSendGrid(to, subject, text, unsubUrl);

        verify(sendGrid, times(1)).api(any(Request.class));
    }

    @Test
    void testSendEmailViaSendGridThrowsException() throws IOException {
        // Arrange
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        // Mock SendGrid to throw an IOException
        doThrow(new IOException("SendGrid API error")).when(sendGrid).api(any(Request.class));

        // Act
        emailService.sendEmailViaSendGrid(to, subject, text, unsubUrl);

        // Assert
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGrid, times(1)).api(captor.capture());
        Request capturedRequest = captor.getValue();

        assertEquals("mail/send", capturedRequest.getEndpoint());
        assertEquals(Method.POST, capturedRequest.getMethod());
        assertNotNull(capturedRequest.getBody());
    }

    @Test
    void testSendEmailViaPostmark() throws IOException, PostmarkException {
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        when(postmark.deliverMessage(any(Message.class))).thenReturn(null);

        emailService.sendEmailViaPostmark(to, subject, text, unsubUrl);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(postmark, times(1)).deliverMessage(captor.capture());
        Message sentMessage = captor.getValue();
        assertEquals(to, sentMessage.getTo());
        assertEquals(subject, sentMessage.getSubject());
    }

    @Test
    void testSendEmailViaPostmarkThrowsException() throws PostmarkException, IOException {
        // Arrange
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        // Mock Postmark to throw an exception
        doThrow(new PostmarkException("Postmark API error", 500))
                .when(postmark).deliverMessage(any(Message.class));

        // Act
        emailService.sendEmailViaPostmark(to, subject, text, unsubUrl);

        // Assert
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(postmark, times(1)).deliverMessage(captor.capture());
        Message capturedMessage = captor.getValue();

        assertEquals("test@example.com", capturedMessage.getFrom());
        assertEquals(to, capturedMessage.getTo());
        assertEquals(subject, capturedMessage.getSubject());
        assertTrue(capturedMessage.getHtmlBody().contains("Test Body"));
    }

    @Test
    void testSendEmailViaSpringMail() {
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        emailService.sendEmailViaSpringMail(to, subject, text, unsubUrl);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        SimpleMailMessage sentMessage = captor.getValue();
        assertEquals(to, sentMessage.getTo()[0]);
        assertEquals(subject, sentMessage.getSubject());
        assertEquals(text, sentMessage.getText());
    }

    @Test
    void testSendEmailViaSpringMailThrowsException() {
        // Arrange
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        // Mock JavaMailSender to throw an exception
        doThrow(new MailException("Spring Mail error") {
        }).when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        emailService.sendEmailViaSpringMail(to, subject, text, unsubUrl);

        // Assert
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        SimpleMailMessage capturedMessage = captor.getValue();

        assertEquals(to, capturedMessage.getTo()[0]);
        assertEquals(subject, capturedMessage.getSubject());
        assertEquals(text, capturedMessage.getText());
    }

    @Test
    void testSendSimpleMessageWithSendGrid() throws IOException {
        when(props.getBroker()).thenReturn(EmailBrokerType.SENDGRID);
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        when(sendGrid.api(any(Request.class))).thenReturn(new Response());

        emailService.sendSimpleMessage(to, subject, text, unsubUrl);

        verify(sendGrid, times(1)).api(any(Request.class));
    }

    @Test
    void testSendSimpleMessageWithPostmark() throws IOException, PostmarkException {
        when(props.getBroker()).thenReturn(EmailBrokerType.POSTMARK);
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        when(postmark.deliverMessage(any(Message.class))).thenReturn(new MessageResponse());

        emailService.sendSimpleMessage(to, subject, text, unsubUrl);

        verify(postmark, times(1)).deliverMessage(any(Message.class));
    }

    @Test
    void testSendSimpleMessageWithSpringMail() {
        when(props.getBroker()).thenReturn(EmailBrokerType.SMTP);
        String to = "recipient@example.com";
        String subject = "Test Subject";
        String text = "Test Body";
        String unsubUrl = "http://localhost/unsubscribe";

        emailService.sendSimpleMessage(to, subject, text, unsubUrl);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    // TODO: Re-work unit test
    // @Test
    // void testEmailList() {
    // List<UserRepresentation> users = new ArrayList<>();
    // UserRepresentation user1 = new UserRepresentation();
    // user1.setEmail("user1@example.com");
    // users.add(user1);

    // UserRepresentation user2 = new UserRepresentation();
    // user2.setEmail("user2@example.com");
    // users.add(user2);

    // String subject = "Test Subject";
    // String text = "Test Body";
    // String unsubUrl = "http://localhost/unsubscribe";

    // when(props.getEmailBroker()).thenReturn("other");

    // emailService.sendEmails(users, subject, text, unsubUrl);

    // verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    // }

    // TODO: Method was removed
    // @Test
    // void testGetNotificationEmailList() {
    // List<UserRepresentation> result =
    // emailService.getNotificationEmailList(EmailFrequency.ONCE_PER_DAY);

    // // TODO: Test underlying logic when method is further implemented
    // assertTrue(result.isEmpty());
    // }
}