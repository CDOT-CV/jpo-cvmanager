package us.dot.its.jpo.ode.api.controllers.advice;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import us.dot.its.jpo.ode.api.services.RsuCredentialManagementService;
import us.dot.its.jpo.ode.api.services.SnmpCredentialManagementService;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(EntityNotFoundException.class)
    public void handleEntityNotFoundException() {
        log.error("Organization not found");
        // TODO: handle exception
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(RsuCredentialManagementService.RsuCredentialAlreadyExistsException.class)
    public void handleRsuCredentialAlreadyExistsException() {
        log.error("RSU Credential already exists");
        // TODO: handle exception
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(SnmpCredentialManagementService.SnmpCredentialAlreadyExistsException.class)
    public void handleSnmpCredentialAlreadyExistsException() {
        log.error("SNMP Credential already exists");
        // TODO: handle exception

    }
}
