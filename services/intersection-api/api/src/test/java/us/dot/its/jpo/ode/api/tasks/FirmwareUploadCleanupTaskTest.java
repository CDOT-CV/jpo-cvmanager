package us.dot.its.jpo.ode.api.tasks;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    }
}
