package us.dot.its.jpo.ode.api.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.ode.api.config.DateTimeConfig;
import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection;
import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjectionImpl;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthDto;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthResponse;

class ScmsHealthMapperTest {

    private ScmsHealthMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new ScmsHealthMapperImpl();
        DateTimeConfig dateTimeConfig = new DateTimeConfig();
        dateTimeConfig.setTimezone("America/Denver");
        mapper.dateTimeConfig = dateTimeConfig;
    }

    @Test
    @DisplayName("Maps projections to ScmsHealthResponse successfully")
    void testToResponse_Success() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        InetAddress inetAddress = InetAddress.getByName(ip);
        Instant expiration = Instant.parse("2024-03-27T15:00:00Z");

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjectionImpl(inetAddress, true, expiration);
        List<ScmsHealthRsuProjection> projections = new ArrayList<>();
        projections.add(projection);

        // Act
        ScmsHealthResponse response = mapper.toResponse(projections);

        // Assert
        assertNotNull(response);
        Map<String, ScmsHealthDto> result = response.getScmsHealthByIp();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(ip));
        ScmsHealthDto dto = result.get(ip);
        assertNotNull(dto);
        assertEquals("1", dto.getHealth());
        // Denver time for 15:00:00 UTC is 09:00:00 AM (Daylight savings)
        assertEquals("03/27/2024 09:00:00 AM", dto.getExpiration());
    }

    @Test
    @DisplayName("Maps projections with inactive health")
    void testToResponse_InactiveHealth_ReturnsDtoWithZero() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        InetAddress inetAddress = InetAddress.getByName(ip);

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjectionImpl(inetAddress, false, null);
        List<ScmsHealthRsuProjection> projections = new ArrayList<>();
        projections.add(projection);

        // Act
        ScmsHealthResponse response = mapper.toResponse(projections);

        // Assert
        assertNotNull(response);
        Map<String, ScmsHealthDto> result = response.getScmsHealthByIp();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(ip));
        assertNotNull(result.get(ip));
        assertEquals("0", result.get(ip).getHealth());
    }

    @Test
    @DisplayName("Maps projections with no health")
    void testToResponse_NoHealth_ReturnsNullValue() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        InetAddress inetAddress = InetAddress.getByName(ip);

        // No health record for this RSU (null health)
        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjectionImpl(inetAddress, null, null);
        List<ScmsHealthRsuProjection> projections = new ArrayList<>();
        projections.add(projection);

        // Act
        ScmsHealthResponse response = mapper.toResponse(projections);

        // Assert
        assertNotNull(response);
        Map<String, ScmsHealthDto> result = response.getScmsHealthByIp();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(ip));
        assertNull(result.get(ip));
    }

    @Test
    @DisplayName("Null input returns null")
    void testToDto_NullInput() {
        assertNull(mapper.toDto(null));
    }

    @Test
    @DisplayName("Maps projections with null health")
    void testToDto_NullHealth() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        InetAddress inetAddress = InetAddress.getByName(ip);

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjectionImpl(inetAddress, null, null);

        // Act
        ScmsHealthDto dto = mapper.toDto(projection);

        // Assert
        assertNotNull(dto);
        assertNull(dto.getHealth());
        assertNull(dto.getExpiration());
    }

    @Test
    @DisplayName("Empty input returns response with empty map")
    void testToResponse_EmptyInput() {
        ScmsHealthResponse response = mapper.toResponse(new ArrayList<>());
        assertNotNull(response);
        assertNotNull(response.getScmsHealthByIp());
        assertTrue(response.getScmsHealthByIp().isEmpty());
    }

    @Test
    @DisplayName("Maps projection to DTO successfully")
    void testToDto_Success() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        InetAddress inetAddress = InetAddress.getByName(ip);
        Instant expiration = Instant.parse("2024-03-27T15:00:00Z");

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjectionImpl(inetAddress, true, expiration);

        // Act
        ScmsHealthDto dto = mapper.toDto(projection);

        // Assert
        assertNotNull(dto);
        assertEquals("1", dto.getHealth());
        assertEquals("03/27/2024 09:00:00 AM", dto.getExpiration());
    }

    @Test
    @DisplayName("Maps projection with different timezone")
    void testToDto_DifferentTimezone() throws UnknownHostException {
        // Arrange - Use UTC timezone
        DateTimeConfig utcConfig = new DateTimeConfig();
        utcConfig.setTimezone("UTC");
        mapper.dateTimeConfig = utcConfig;

        String ip = "10.0.0.1";
        InetAddress inetAddress = InetAddress.getByName(ip);
        Instant expiration = Instant.parse("2024-03-27T15:00:00Z");

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjectionImpl(inetAddress, true, expiration);

        // Act
        ScmsHealthDto dto = mapper.toDto(projection);

        // Assert
        assertNotNull(dto);
        assertEquals("1", dto.getHealth());
        // UTC time should be 15:00:00 (3:00 PM)
        assertEquals("03/27/2024 03:00:00 PM", dto.getExpiration());
    }
}
