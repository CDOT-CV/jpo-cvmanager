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

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import us.dot.its.jpo.ode.api.models.postgres.projections.ScmsHealthRsuProjection;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.ScmsHealth;
import us.dot.its.jpo.ode.api.models.scms.ScmsHealthDto;

class ScmsHealthMapperTest {

    private final ScmsHealthMapper mapper = Mappers.getMapper(ScmsHealthMapper.class);

    @Test
    void testToMap_Success() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(ip));

        Instant expiration = Instant.parse("2024-03-27T15:00:00Z");
        ScmsHealth sh = new ScmsHealth();
        sh.setHealth(true);
        sh.setExpiration(expiration);

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjection(rsu, sh);
        List<ScmsHealthRsuProjection> projections = new ArrayList<>();
        projections.add(projection);

        // Act
        Map<String, ScmsHealthDto> result = mapper.toMap(projections);

        // Assert
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
    void testToMap_InactiveHealth_ReturnsDtoWithZero() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(ip));

        ScmsHealth sh = new ScmsHealth();
        sh.setHealth(false); // Inactive

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjection(rsu, sh);
        List<ScmsHealthRsuProjection> projections = new ArrayList<>();
        projections.add(projection);

        // Act
        Map<String, ScmsHealthDto> result = mapper.toMap(projections);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(ip));
        assertNotNull(result.get(ip));
        assertEquals("0", result.get(ip).getHealth());
    }

    @Test
    void testToMap_NoHealth_ReturnsNullValue() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(ip));

        // No ScmsHealth record for this RSU
        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjection(rsu, null);
        List<ScmsHealthRsuProjection> projections = new ArrayList<>();
        projections.add(projection);

        // Act
        Map<String, ScmsHealthDto> result = mapper.toMap(projections);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(ip));
        assertNull(result.get(ip));
    }

    @Test
    void testToDto_NullInput() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void testToDto_NullScmsHealth() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(ip));

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjection(rsu, null);

        // Act
        ScmsHealthDto dto = mapper.toDto(projection);

        // Assert
        assertNotNull(dto);
        assertNull(dto.getHealth());
        assertNull(dto.getExpiration());
    }

    @Test
    void testToMap_EmptyInput() {
        Map<String, ScmsHealthDto> result = mapper.toMap(new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testToDto_Success() throws UnknownHostException {
        // Arrange
        String ip = "10.0.0.1";
        Rsu rsu = new Rsu();
        rsu.setIpv4Address(InetAddress.getByName(ip));

        Instant expiration = Instant.parse("2024-03-27T15:00:00Z");
        ScmsHealth sh = new ScmsHealth();
        sh.setHealth(true);
        sh.setExpiration(expiration);

        ScmsHealthRsuProjection projection = new ScmsHealthRsuProjection(rsu, sh);

        // Act
        ScmsHealthDto dto = mapper.toDto(projection);

        // Assert
        assertNotNull(dto);
        assertEquals("1", dto.getHealth());
        assertEquals("03/27/2024 09:00:00 AM", dto.getExpiration());
    }
}
