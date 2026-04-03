package us.dot.its.jpo.ode.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeConfigTest {

    @Test
    @DisplayName("Default timezone is America/Denver")
    void testDefaultTimezone() {
        DateTimeConfig config = new DateTimeConfig();

        assertEquals("America/Denver", config.getTimezone());
        assertEquals(ZoneId.of("America/Denver"), config.getZoneId());
    }

    @Test
    @DisplayName("America/Denver timezone is valid")
    void testAmericaDenverTimezone() {
        DateTimeConfig config = new DateTimeConfig();
        config.setTimezone("America/Denver");

        ZoneId zoneId = config.getZoneId();

        assertNotNull(zoneId);
        assertEquals("America/Denver", zoneId.getId());
    }

    @Test
    @DisplayName("UTC timezone is valid")
    void testUtcTimezone() {
        DateTimeConfig config = new DateTimeConfig();
        config.setTimezone("UTC");

        ZoneId zoneId = config.getZoneId();

        assertNotNull(zoneId);
        assertEquals("UTC", zoneId.getId());
    }

    @Test
    @DisplayName("America/New_York timezone is valid")
    void testAmericaNewYorkTimezone() {
        DateTimeConfig config = new DateTimeConfig();
        config.setTimezone("America/New_York");

        ZoneId zoneId = config.getZoneId();

        assertNotNull(zoneId);
        assertEquals("America/New_York", zoneId.getId());
    }

    @Test
    @DisplayName("Unrecognized timezone throws DateTimeException")
    void testInvalidTimezoneThrowsException() {
        DateTimeConfig config = new DateTimeConfig();
        config.setTimezone("Invalid/Timezone");

        assertThrows(DateTimeException.class, config::getZoneId);
    }

    @Test
    @DisplayName("Empty timezone throws exception")
    void testEmptyTimezoneThrowsException() {
        DateTimeConfig config = new DateTimeConfig();
        config.setTimezone("");

        assertThrows(DateTimeException.class, config::getZoneId);
    }

    @Test
    @DisplayName("Null timezone throws NullPointerException")
    void testNullTimezoneThrowsException() {
        DateTimeConfig config = new DateTimeConfig();
        config.setTimezone(null);

        assertThrows(NullPointerException.class, config::getZoneId);
    }

    @Test
    @DisplayName("Timezone can be changed and getZoneId reflects the change")
    void testTimezoneCanBeChanged() {
        DateTimeConfig config = new DateTimeConfig();

        config.setTimezone("America/Denver");
        assertEquals(ZoneId.of("America/Denver"), config.getZoneId());

        config.setTimezone("UTC");
        assertEquals(ZoneId.of("UTC"), config.getZoneId());

        config.setTimezone("America/New_York");
        assertEquals(ZoneId.of("America/New_York"), config.getZoneId());
    }
}

