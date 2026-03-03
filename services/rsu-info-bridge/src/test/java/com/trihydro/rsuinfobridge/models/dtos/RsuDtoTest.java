package com.trihydro.rsuinfobridge.models.dtos;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RsuDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private RsuDto.RsuDtoBuilder validRsuDtoBuilder() {
        return RsuDto.builder()
                .id("1")
                .ipv4Address("10.0.0.1")
                .snmpProtocol("NTCIP1218")
                .snmpUsername("testuser")
                .snmpPassword("testpass")
                .latitude(39.73915)
                .longitude(-104.9847)
                .timDepositEnabled(true);
    }

    @Test
    void validRsuDto_shouldPassValidation() {
        RsuDto dto = validRsuDtoBuilder().build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "Valid RsuDto should have no violations");
    }

    @Test
    void validRsuDto_withOptionalFieldsNull_shouldPassValidation() {
        RsuDto dto = validRsuDtoBuilder()
                .authenticationProtocol(null)
                .privacyProtocol(null)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "RsuDto with null optional fields should have no violations");
    }

    @Test
    void validRsuDto_withOptionalFieldsSet_shouldPassValidation() {
        RsuDto dto = validRsuDtoBuilder()
                .authenticationProtocol("SHA")
                .privacyProtocol("AES")
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "RsuDto with optional fields set should have no violations");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void id_whenBlank_shouldFailValidation(String id) {
        RsuDto dto = validRsuDtoBuilder()
                .id(id)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Blank id should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("id")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void ipv4Address_whenBlank_shouldFailValidation(String ipv4Address) {
        RsuDto dto = validRsuDtoBuilder()
                .ipv4Address(ipv4Address)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Blank ipv4Address should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("ipv4Address")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void snmpProtocol_whenBlank_shouldFailValidation(String snmpProtocol) {
        RsuDto dto = validRsuDtoBuilder()
                .snmpProtocol(snmpProtocol)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Blank snmpProtocol should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("snmpProtocol")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void snmpUsername_whenBlank_shouldFailValidation(String snmpUsername) {
        RsuDto dto = validRsuDtoBuilder()
                .snmpUsername(snmpUsername)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Blank snmpUsername should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("snmpUsername")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void snmpPassword_whenBlank_shouldFailValidation(String snmpPassword) {
        RsuDto dto = validRsuDtoBuilder()
                .snmpPassword(snmpPassword)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Blank snmpPassword should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("snmpPassword")));
    }

    @Test
    void latitude_whenNull_shouldFailValidation() {
        RsuDto dto = validRsuDtoBuilder()
                .latitude(null)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Null latitude should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("latitude")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("latitude is required")));
    }

    @Test
    void longitude_whenNull_shouldFailValidation() {
        RsuDto dto = validRsuDtoBuilder()
                .longitude(null)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Null longitude should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("longitude")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("longitude is required")));
    }

    @Test
    void timDepositEnabled_whenNull_shouldFailValidation() {
        RsuDto dto = validRsuDtoBuilder()
                .timDepositEnabled(null)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Null timDepositEnabled should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("timDepositEnabled")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("timDepositEnabled is required")));
    }

    @Test
    void multipleFieldsInvalid_shouldReturnMultipleViolations() {
        RsuDto dto = RsuDto.builder()
                .id(null)
                .ipv4Address("")
                .snmpProtocol("   ")
                .snmpUsername(null)
                .snmpPassword(null)
                .latitude(null)
                .longitude(null)
                .timDepositEnabled(null)
                .build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertEquals(8, violations.size(), "All 8 required fields should have violations");
    }

    @Test
    void validationMessages_shouldBeCorrect() {
        RsuDto dto = RsuDto.builder().build();

        Set<ConstraintViolation<RsuDto>> violations = validator.validate(dto);

        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("id is required")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("ipv4Address is required")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("snmpProtocol is required")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("snmpUsername is required")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("snmpPassword is required")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("latitude is required")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("longitude is required")));
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("timDepositEnabled is required")));
    }
}

