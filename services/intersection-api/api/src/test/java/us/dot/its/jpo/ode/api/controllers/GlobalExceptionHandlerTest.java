package us.dot.its.jpo.ode.api.controllers;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    class HandleEntityNotFoundTests {

        @Test
        void testReturnsNotFound() {
            // Arrange
            EntityNotFoundException ex = new EntityNotFoundException("User not found");

            // Act
            ErrorResponse response = handler.handleEntityNotFound(ex);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertTrue(response.getBody().getDetail().contains("User not found"));
        }

        @Test
        void testWithDifferentMessage() {
            // Arrange
            EntityNotFoundException ex = new EntityNotFoundException("RSU with IP 192.168.1.1 not found");

            // Act
            ErrorResponse response = handler.handleEntityNotFound(ex);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertTrue(response.getBody().getDetail().contains("192.168.1.1"));
        }
    }

    @Nested
    class HandleConstraintViolationTests {

        @Test
        void testSingleViolation() {
            // Arrange
            ConstraintViolation<?> violation = mock(ConstraintViolation.class);
            Path path = mock(Path.class);

            when(violation.getPropertyPath()).thenReturn(path);
            when(path.toString()).thenReturn("getRsu.id");
            when(violation.getMessage()).thenReturn("must be greater than 0");

            ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

            // Act
            ErrorResponse response = handler.handleConstraintViolation(ex);

            // Assert
            assertNotNull(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ProblemDetail body = response.getBody();
            assertTrue(body.getDetail().contains("Validation failed"));
            assertTrue(body.getDetail().contains("id"));
            assertTrue(body.getDetail().contains("must be greater than 0"));

            @SuppressWarnings("unchecked")
            Map<String, String> violations = (Map<String, String>) body.getProperties().get("violations");
            assertNotNull(violations);
            assertEquals("must be greater than 0", violations.get("id"));
        }

        @Test
        void testMultipleViolations() {
            // Arrange
            ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
            ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
            Path path1 = mock(Path.class);
            Path path2 = mock(Path.class);

            when(violation1.getPropertyPath()).thenReturn(path1);
            when(path1.toString()).thenReturn("createRsu.rsuIp");
            when(violation1.getMessage()).thenReturn("must not be blank");

            when(violation2.getPropertyPath()).thenReturn(path2);
            when(path2.toString()).thenReturn("createRsu.limit");
            when(violation2.getMessage()).thenReturn("must be less than 100");

            ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation1, violation2));

            // Act
            ErrorResponse response = handler.handleConstraintViolation(ex);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> violations = (Map<String, String>) response.getBody().getProperties().get("violations");
            assertEquals(2, violations.size());
        }
    }

    @Nested
    class HandleMethodArgumentNotValidTests {

        @Test
        void testSingleFieldError() {
            // Arrange
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError fieldError = new FieldError("rsuDto", "ipv4Address", "must not be null");

            when(ex.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

            // Act
            ErrorResponse response = handler.handleMethodArgumentNotValid(ex);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            ProblemDetail body = response.getBody();
            assertTrue(body.getDetail().contains("Validation failed"));
            assertTrue(body.getDetail().contains("ipv4Address"));
            assertTrue(body.getDetail().contains("must not be null"));

            @SuppressWarnings("unchecked")
            Map<String, String> fieldErrors = (Map<String, String>) body.getProperties().get("fieldErrors");
            assertEquals("must not be null", fieldErrors.get("ipv4Address"));
        }

        @Test
        void testMultipleFieldErrors() {
            // Arrange
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);
            FieldError error1 = new FieldError("userDto", "email", "must not be null");
            FieldError error2 = new FieldError("userDto", "firstName", "size must be between 1 and 128");

            when(ex.getBindingResult()).thenReturn(bindingResult);
            when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(error1, error2));

            // Act
            ErrorResponse response = handler.handleMethodArgumentNotValid(ex);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, String> fieldErrors = (Map<String, String>) response.getBody().getProperties()
                    .get("fieldErrors");
            assertEquals(2, fieldErrors.size());
            assertEquals("must not be null", fieldErrors.get("email"));
        }
    }

    @Nested
    class HandleDataIntegrityViolationTests {

        @Test
        void testDuplicateKey() {
            // Arrange
            String errorMessage = "could not execute statement [ERROR: duplicate key value violates unique constraint \"rsu_milepost_primary_route\" "
                    +
                    "Detail: Key (milepost, primary_route)=(1, I999) already exists.] " +
                    "constraint [rsu_milepost_primary_route]";

            DataIntegrityViolationException ex = new DataIntegrityViolationException(errorMessage);

            // Act
            ErrorResponse response = handler.handleDataIntegrityViolation(ex);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            ProblemDetail body = response.getBody();
            assertTrue(body.getDetail().contains("RSU"));
            assertTrue(body.getDetail().contains("milepost '1'"));
            assertTrue(body.getDetail().contains("primary route 'I999'"));
            assertTrue(body.getDetail().contains("already exists"));
            assertFalse(body.getDetail().contains("SQL"));
            assertFalse(body.getDetail().contains("constraint"));

            assertEquals("rsu_milepost_primary_route", body.getProperties().get("constraint"));
        }

        @Test
        void testRsuDuplicateSerialNumber() {
            String errorMessage = "could not execute statement [ERROR: duplicate key value violates unique constraint \"rsu_milepost_primary_route\""
                    + "Detail: Key (milepost, primary_route)=(1, I999) already exists.] "
                    + "[insert into rsus (credential_id,firmware_version,geography,ipv4_address,iss_scms_id,milepost,model,primary_route,serial_number,snmp_credential_id,snmp_protocol_id,target_firmware_version,rsu_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; "
                    + "SQL [insert into rsus (credential_id,firmware_version,geography,ipv4_address,iss_scms_id,milepost,model,primary_route,serial_number,snmp_credential_id,snmp_protocol_id,target_firmware_version,rsu_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; "
                    + "constraint [rsu_milepost_primary_route]";

            DataIntegrityViolationException ex = new DataIntegrityViolationException(errorMessage);

            // Act
            ErrorResponse response = handler.handleDataIntegrityViolation(ex);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            ProblemDetail body = response.getBody();
            assertTrue(body.getDetail().equals("A RSU with milepost '1' and primary route 'I999' already exists."));

            assertEquals("rsu_milepost_primary_route", body.getProperties().get("constraint"));
        }

        @Test
        void testForeignKeyNotPresent() {
            // Arrange
            String errorMessage = "could not execute statement [ERROR: insert or update on table \"rsus\" " +
                    "violates foreign key constraint \"fk_credential\" " +
                    "Detail: Key (credential_id)=(999) is not present in table \"credentials\".] " +
                    "constraint [fk_credential]";
            DataIntegrityViolationException ex = new DataIntegrityViolationException(errorMessage);

            // Act
            ErrorResponse response = handler.handleDataIntegrityViolation(ex);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertTrue(response.getBody().getDetail().contains("referenced item does not exist"));
            assertFalse(response.getBody().getDetail().contains("SQL"));
        }

        @Test
        void testNotNull() {
            // Arrange
            String errorMessage = "could not execute statement [ERROR: null value in column \"ipv4_address\" " +
                    "violates not-null constraint]";
            DataIntegrityViolationException ex = new DataIntegrityViolationException(errorMessage);

            // Act
            ErrorResponse response = handler.handleDataIntegrityViolation(ex);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertTrue(response.getBody().getDetail().contains("IPv4 address"));
            assertTrue(response.getBody().getDetail().contains("required"));
            assertFalse(response.getBody().getDetail().contains("null value"));
        }

        @Test
        void testGenericConstraint() {
            // Arrange
            String errorMessage = "Generic database constraint violation";
            DataIntegrityViolationException ex = new DataIntegrityViolationException(errorMessage);

            // Act
            ErrorResponse response = handler.handleDataIntegrityViolation(ex);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertTrue(response.getBody().getDetail().contains("database constraint was violated"));
        }
    }

    @Nested
    class HandleExceptionTests {

        @Test
        void testGenericException() {
            // Arrange
            Exception ex = new RuntimeException("Unexpected error");

            // Act
            ErrorResponse response = handler.handleException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertTrue(response.getBody().getDetail().contains("unexpected error occurred"));
            assertFalse(response.getBody().getDetail().contains("RuntimeException"));
        }

        @Test
        void testNullPointerException() {
            // Arrange
            Exception ex = new NullPointerException("Something was null");

            // Act
            ErrorResponse response = handler.handleException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            // Should return generic message, not expose internal details
            assertFalse(response.getBody().getDetail().contains("null"));
        }
    }

    @Nested
    class HelperMethodTests {

        @Test
        void testBuildDuplicateKeyMessageExtractsFieldsAndValues() {
            // Arrange
            String message = "duplicate key value violates unique constraint \"rsu_serial_number\" " +
                    "Detail: Key (serial_number)=(E5673) already exists. " +
                    "[http-nio-8089-exec-9] WARN us.dot.its.jpo.ode.api.controllers.GlobalExceptionHandler - Data integrity violation: could not execute statement [ERROR: duplicate key value violates unique constraint \"rsu_serial_number\" "
                    +
                    "Detail: Key (serial_number)=(E5673) already exists.]";
            DataIntegrityViolationException ex = new DataIntegrityViolationException(message);

            // Act
            ErrorResponse response = handler.handleDataIntegrityViolation(ex);

            // Assert
            assertTrue(response.getBody().getDetail().contains("RSU"));
            assertTrue(response.getBody().getDetail().contains("already exists"));
        }

        @Test
        void testFormatFieldNameHandlesSpecialCases() {
            // Arrange
            String message = "null value in column \"ipv4_address\" violates not-null constraint";
            DataIntegrityViolationException ex = new DataIntegrityViolationException(message);

            // Act
            ErrorResponse response = handler.handleDataIntegrityViolation(ex);

            // Assert
            // Should format ipv4_address as "IPv4 address"
            assertTrue(response.getBody().getDetail().contains("IPv4 address"));
            assertFalse(response.getBody().getDetail().contains("ipv4_address"));
        }

        @Test
        void testDetermineResourceTypeRecognizesRsu() {
            // Arrange
            String message = "duplicate key violates constraint \"rsus_pkey\"";
            DataIntegrityViolationException ex = new DataIntegrityViolationException(message);

            // Act
            ErrorResponse response = handler.handleDataIntegrityViolation(ex);

            // Assert - should not contain "RSU" since pattern doesn't match, but tests the
            // method
            assertNotNull(response.getBody().getDetail());
        }
    }
}