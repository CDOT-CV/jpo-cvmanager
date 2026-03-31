package us.dot.its.jpo.ode.api.mappers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeCheckResponseDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeResultDto;

class FirmwareUpgradeMapperTest {

    // FirmwareUpgradeMapper is an interface with only default methods; instantiate
    // directly as an anonymous implementation to test the mapping logic.
    private final FirmwareUpgradeMapper mapper = new FirmwareUpgradeMapper() {
    };

    @Nested
    @DisplayName("Tests for mapCheckUpgradeResponse mapper method")
    class MapCheckUpgradeResponseTests {

        @Test
        void testMapCheckUpgradeResponse_Success() {
            Map<String, Object> response = Map.of(
                    "upgrade_available", true,
                    "upgrade_id", 3L,
                    "upgrade_name", "RSU Firmware 3.0",
                    "upgrade_version", "3.0.1");

            FirmwareUpgradeCheckResponseDto result = mapper.mapCheckUpgradeResponse(response);

            assertNotNull(result);
            assertTrue(result.getUpgradeAvailable());
            assertEquals(3L, result.getUpgradeId());
            assertEquals("RSU Firmware 3.0", result.getUpgradeName());
            assertEquals("3.0.1", result.getUpgradeVersion());
        }

        @Test
        void testMapCheckUpgradeResponse_UpgradeNotAvailable() {
            Map<String, Object> response = Map.of(
                    "upgrade_available", false,
                    "upgrade_id", -1,
                    "upgrade_name", "",
                    "upgrade_version", "");

            FirmwareUpgradeCheckResponseDto result = mapper.mapCheckUpgradeResponse(response);

            assertNotNull(result);
            assertFalse(result.getUpgradeAvailable());
            assertEquals(-1L, result.getUpgradeId());
            assertEquals("", result.getUpgradeName());
            assertEquals("", result.getUpgradeVersion());
        }

        @Test
        void testMapCheckUpgradeResponse_Null() {
            FirmwareUpgradeCheckResponseDto result = mapper.mapCheckUpgradeResponse(null);

            assertNull(result);
        }

        @Test
        void testMapCheckUpgradeResponse_UpgradeIdAsInteger() {
            // The service returns Integer; mapper should widen it to Long
            Map<String, Object> response = Map.of(
                    "upgrade_available", true,
                    "upgrade_id", 42,
                    "upgrade_name", "Firmware v4",
                    "upgrade_version", "4.0");

            FirmwareUpgradeCheckResponseDto result = mapper.mapCheckUpgradeResponse(response);

            assertNotNull(result);
            assertEquals(42L, result.getUpgradeId());
        }

        @Test
        void testMapCheckUpgradeResponse_UpgradeIdNonNumber_DefaultsToMinusOne() {
            Map<String, Object> response = Map.of(
                    "upgrade_available", true,
                    "upgrade_id", "not-a-number",
                    "upgrade_name", "Firmware",
                    "upgrade_version", "1.0");

            FirmwareUpgradeCheckResponseDto result = mapper.mapCheckUpgradeResponse(response);

            assertNotNull(result);
            assertEquals(-1L, result.getUpgradeId());
        }

        @Test
        void testMapCheckUpgradeResponse_MissingOptionalFields_DefaultToEmpty() {
            // Only required key present; name and version should default to ""
            Map<String, Object> response = Map.of(
                    "upgrade_available", false,
                    "upgrade_id", 0);

            FirmwareUpgradeCheckResponseDto result = mapper.mapCheckUpgradeResponse(response);

            assertNotNull(result);
            assertEquals("", result.getUpgradeName());
            assertEquals("", result.getUpgradeVersion());
        }
    }

    @Nested
    @DisplayName("Tests for mapStartUpgradeResponse mapper method")
    class MapStartUpgradeResponseTests {

        @Test
        void testMapStartUpgradeResponse_SingleRsuSuccess() {
            Map<String, Object> response = Map.of(
                    "10.0.0.1", Map.of("code", 200, "data", Map.of("message", "started")));

            Map<String, FirmwareUpgradeResultDto> result = mapper.mapStartUpgradeResponse(response);

            assertNotNull(result);
            assertEquals(1, result.size());
            FirmwareUpgradeResultDto dto = result.get("10.0.0.1");
            assertNotNull(dto);
            assertEquals(200, dto.getCode());
            assertEquals(Map.of("message", "started"), dto.getData());
        }

        @Test
        void testMapStartUpgradeResponse_MultipleRsus() {
            Map<String, Object> response = Map.of(
                    "10.0.0.1", Map.of("code", 200, "data", Map.of("message", "started")),
                    "10.0.0.2", Map.of("code", 409, "data", "already up to date"),
                    "10.0.0.3", Map.of("code", 404, "data", "RSU not found"));

            Map<String, FirmwareUpgradeResultDto> result = mapper.mapStartUpgradeResponse(response);

            assertEquals(3, result.size());
            assertEquals(200, result.get("10.0.0.1").getCode());
            assertEquals(409, result.get("10.0.0.2").getCode());
            assertEquals(404, result.get("10.0.0.3").getCode());
        }

        @Test
        void testMapStartUpgradeResponse_Null_ReturnsEmptyMap() {
            Map<String, FirmwareUpgradeResultDto> result = mapper.mapStartUpgradeResponse(null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void testMapStartUpgradeResponse_EntryValueNotMap_IsIgnored() {
            // Entries whose value is not a Map should be skipped
            Map<String, Object> response = Map.of(
                    "10.0.0.1", "not-a-map-value");

            Map<String, FirmwareUpgradeResultDto> result = mapper.mapStartUpgradeResponse(response);

            assertTrue(result.isEmpty());
        }

        @Test
        void testMapStartUpgradeResponse_MissingDataField_DefaultsToEmpty() {
            Map<String, Object> response = Map.of(
                    "10.0.0.1", Map.of("code", 201));

            Map<String, FirmwareUpgradeResultDto> result = mapper.mapStartUpgradeResponse(response);

            FirmwareUpgradeResultDto dto = result.get("10.0.0.1");
            assertNotNull(dto);
            assertEquals(201, dto.getCode());
            assertEquals("", dto.getData());
        }

        @Test
        void testMapStartUpgradeResponse_MissingCodeField_CodeIsNull() {
            // When "code" key is absent, code should be null
            Map<String, Object> response = Map.of(
                    "10.0.0.1", Map.of("data", "some data"));

            Map<String, FirmwareUpgradeResultDto> result = mapper.mapStartUpgradeResponse(response);

            FirmwareUpgradeResultDto dto = result.get("10.0.0.1");
            assertNotNull(dto);
            assertNull(dto.getCode());
        }

        @Test
        void testMapStartUpgradeResponse_CodeAsNonNumber_DefaultsTo500() {
            Map<String, Object> response = Map.of(
                    "10.0.0.1", Map.of("code", "not-a-number", "data", "error"));

            Map<String, FirmwareUpgradeResultDto> result = mapper.mapStartUpgradeResponse(response);

            assertEquals(500, result.get("10.0.0.1").getCode());
        }
    }
}
