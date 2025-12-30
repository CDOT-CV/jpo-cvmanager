package us.dot.its.jpo.ode.api.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import us.dot.its.jpo.ode.api.models.emails.EmailSendResponse;
import us.dot.its.jpo.ode.api.models.emails.contents.ApiErrorEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.FirmwareUpgradeFailureEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.RsuErrorSummaryEmailContents;
import us.dot.its.jpo.ode.api.models.emails.contents.message_counts.MessageCountEmailContents;
import us.dot.its.jpo.ode.api.services.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/emails")
@RequiredArgsConstructor
public class EmailController {
    private final EmailService emailService;

    // TODO: Authenticate these services for non-user access
    @Operation(summary = "Send Message Counts Emails", description = "Send message counts emails")
    @RequestMapping(value = "/send-message-counts", method = RequestMethod.POST, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<String> sendMessageCountsEmails(
            @RequestBody MessageCountEmailContents body) {

        return EmailSendResponse.getCombinedResponseEntity(emailService.sendMessageCounts(body));
    }

    @Operation(summary = "Send Firmware Upgrade Failure Emails", description = "Send firmware upgrade failure emails")
    @RequestMapping(value = "/send-firmware-upgrade-failure", method = RequestMethod.POST, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<String> sendFirmwareUpgradeFailureEmails(
            @RequestBody FirmwareUpgradeFailureEmailContents body) {

        return EmailSendResponse.getCombinedResponseEntity(emailService.sendFirmwareUpgradeFailure(body));
    }

    @Operation(summary = "API Error Summary", description = "Request access to an organization")
    @RequestMapping(value = "/send-api-error", method = RequestMethod.POST, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<String> sendApiErrorEmails(
            @RequestBody ApiErrorEmailContents body) {

        return EmailSendResponse.getCombinedResponseEntity(emailService.sendApiError(body));
    }

    @Operation(summary = "Rsu Error Summary", description = "Request access to an organization")
    @RequestMapping(value = "/send-rsu-error-summary", method = RequestMethod.POST, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('USER')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid message body"),
    })
    public @ResponseBody ResponseEntity<String> sendRsuErrorSummaryEmails(
            @RequestBody RsuErrorSummaryEmailContents body) {

        return EmailSendResponse.getCombinedResponseEntity(emailService.sendRsuErrorSummary(body));
    }
}