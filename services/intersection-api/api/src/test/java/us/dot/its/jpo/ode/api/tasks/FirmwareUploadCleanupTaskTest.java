package us.dot.its.jpo.ode.api.tasks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUploadStatus;
import us.dot.its.jpo.ode.api.repositories.FirmwareUploadRepository;
import us.dot.its.jpo.ode.api.services.FirmwareUploadCleanupProperties;

class FirmwareUploadCleanupTaskTest {
    private final FirmwareUploadRepository repository = mock(FirmwareUploadRepository.class);
    private final FirmwareUploadCleanupProperties properties = new FirmwareUploadCleanupProperties();
    private final Instant now = Instant.parse("2026-09-03T20:00:00Z");

    private FirmwareUploadCleanupTask task;

    @BeforeEach
    void setUp() {
        properties.setExpirationGrace(Duration.ofHours(1));
        properties.setRetention(Duration.ofDays(30));
        task = new FirmwareUploadCleanupTask(repository, properties);
    }

    @Test
    void expiresStalePendingRowsAndPurgesOldTerminalRows() {
        when(repository.expirePendingUploads(
                FirmwareUploadStatus.PENDING,
                FirmwareUploadStatus.EXPIRED,
                now.minus(Duration.ofHours(1)),
                now,
                FirmwareUploadCleanupTask.EXPIRATION_REASON)).thenReturn(3);
        when(repository.deleteFinishedUploadsBefore(
                List.of(FirmwareUploadStatus.FAILED, FirmwareUploadStatus.EXPIRED),
                now.minus(Duration.ofDays(30)))).thenReturn(2);

        task.cleanUpFirmwareUploads(now);

        verify(repository).expirePendingUploads(
                FirmwareUploadStatus.PENDING,
                FirmwareUploadStatus.EXPIRED,
                now.minus(Duration.ofHours(1)),
                now,
                FirmwareUploadCleanupTask.EXPIRATION_REASON);
        verify(repository).deleteFinishedUploadsBefore(
                List.of(FirmwareUploadStatus.FAILED, FirmwareUploadStatus.EXPIRED),
                now.minus(Duration.ofDays(30)));
    }

    @Test
    void rejectsInvalidRetentionConfiguration() {
        properties.setRetention(Duration.ZERO);

        assertThatThrownBy(() -> task.cleanUpFirmwareUploads(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention");
        verifyNoInteractions(repository);
    }

    static Stream<Duration> invalidGracePeriods() {
        return Stream.of(null, Duration.ofSeconds(-1));
    }

    @ParameterizedTest
    @MethodSource("invalidGracePeriods")
    void rejectsInvalidGraceBeforeChangingRecords(Duration grace) {
        properties.setExpirationGrace(grace);
        assertThatThrownBy(() -> task.cleanUpFirmwareUploads(now))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("expiration grace");
        verifyNoInteractions(repository);
    }

    static Stream<Duration> invalidRetentions() {
        return Stream.of(null, Duration.ofSeconds(-1));
    }

    @ParameterizedTest
    @MethodSource("invalidRetentions")
    void rejectsMissingOrNegativeRetentionBeforeChangingRecords(Duration retention) {
        properties.setRetention(retention);
        assertThatThrownBy(() -> task.cleanUpFirmwareUploads(now))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("retention");
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsZeroGracePeriod() {
        properties.setExpirationGrace(Duration.ZERO);
        task.cleanUpFirmwareUploads(now);
        verify(repository).expirePendingUploads(FirmwareUploadStatus.PENDING, FirmwareUploadStatus.EXPIRED,
                now, now, FirmwareUploadCleanupTask.EXPIRATION_REASON);
        verify(repository).deleteFinishedUploadsBefore(
                List.of(FirmwareUploadStatus.FAILED, FirmwareUploadStatus.EXPIRED), now.minus(Duration.ofDays(30)));
    }
}
