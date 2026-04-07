package us.dot.its.jpo.ode.api.models.emails;

import java.util.List;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmailApiResponse {
    private List<EmailSendResponse> responses;
    private int successCount;
    private int failureCount;

    public HttpStatus getHttpStatus() {
        if (failureCount == 0) {
            return HttpStatus.OK; // 200
        } else if (successCount > 0) {
            return HttpStatus.MULTI_STATUS; // 207
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR; // 500
        }
    }

    public boolean hasPartialFailure() {
        return successCount > 0 && failureCount > 0;
    }

    public boolean hasCompleteFailure() {
        return successCount == 0 && failureCount > 0;
    }
}
