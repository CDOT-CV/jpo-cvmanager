package us.dot.its.jpo.ode.api.models.emails;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.ResponseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmailSendResponse {
    private Integer statusCode;
    private String message;

    public ResponseEntity<String> getResponseEntity() {
        return ResponseEntity.status(getMappedStatusCode()).body(this.message);
    }

    private Integer getMappedStatusCode() {
        if (statusCode.equals(0))
            return 200;
        return statusCode;
    }

    public static ResponseEntity<String> getCombinedResponseEntity(List<EmailSendResponse> response) {
        // return highest numbered status code with message
        EmailSendResponse highest = response.stream()
                .max(Comparator.comparing(EmailSendResponse::getMappedStatusCode))
                .orElse(new EmailSendResponse(200, "No emails sent"));

        return highest.getResponseEntity();
    }
}
