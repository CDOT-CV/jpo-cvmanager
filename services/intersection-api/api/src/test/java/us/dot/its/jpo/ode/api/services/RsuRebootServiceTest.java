package us.dot.its.jpo.ode.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import us.dot.its.jpo.ode.api.config.RsuRebootProperties;
import us.dot.its.jpo.ode.api.config.RsuRebootConfig;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuCredential;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuModel;
import us.dot.its.jpo.ode.api.models.postgres.tables.Manufacturer;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.services.ssh.SshRebootClient;

class RsuRebootServiceTest {
    private final RsuRepository repository = mock(RsuRepository.class);
    private final SshRebootClient client = mock(SshRebootClient.class);
    private final List<Runnable> tasks = new ArrayList<>();
    private final RsuRebootProperties properties = new RsuRebootProperties();
    private final InetAddress address = RsuManagementService.toIpv4Address("192.0.2.10");
    private RsuRebootService service;
    private RsuCredential credentials;
    private Rsu rsu;
    private Manufacturer manufacturer;

    @BeforeEach
    void setup() throws Exception {
        credentials = new RsuCredential();
        credentials.setUsername("test-user");
        credentials.setPassword(java.util.UUID.randomUUID().toString());
        rsu = new Rsu();
        manufacturer = new Manufacturer();
        manufacturer.setName("Commsignia");
        var model = new RsuModel();
        model.setManufacturer(manufacturer);
        rsu.setModel(model);
        rsu.setCredential(credentials);
        when(repository.findByIpv4Address(address)).thenReturn(rsu);
        when(client.reboot(any(), anyString(), anyString())).thenReturn(SshRebootClient.Outcome.COMMAND_ACCEPTED);
        service = new RsuRebootService(repository, client, tasks::add);
    }

    @Test
    void acceptsBeforeSshRunsAndRejectsDuplicateUntilCompletion() {
        assertNotNull(service.requestReboot(address));
        verifyNoInteractions(client);
        assertStatus(HttpStatus.CONFLICT, () -> service.requestReboot(address));
        tasks.removeFirst().run();
        verify(client).reboot(address, credentials.getUsername(), credentials.getPassword());
        assertNotNull(service.requestReboot(address));
    }

    @Test
    void saturatedExecutorDoesNotLeaveDeviceLocked() {
        TaskExecutor executor = mock(TaskExecutor.class);
        doThrow(new RejectedExecutionException()).doNothing().when(executor).execute(any());
        service = new RsuRebootService(repository, client, executor);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.requestReboot(address));
        assertNotNull(service.requestReboot(address));
        verifyNoInteractions(client);
    }

    @Test
    void configuredExecutorRejectsInsteadOfQueuingAnotherReboot() throws Exception {
        properties.setConcurrency(1);
        var executor = new RsuRebootConfig().rsuRebootExecutor(properties, client);
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        executor.initialize();
        try {
            executor.execute(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(running.await(5, TimeUnit.SECONDS));
            service = new RsuRebootService(repository, client, executor);
            assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.requestReboot(address));
            assertTrue(executor.getThreadPoolExecutor().getQueue().isEmpty());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void workerFailureReleasesAdmissionWithoutRetrying() {
        when(client.reboot(any(), anyString(), anyString())).thenThrow(new IllegalStateException("device output"));
        service.requestReboot(address);
        tasks.removeFirst().run();
        assertNotNull(service.requestReboot(address));
        verify(client, times(1)).reboot(any(), anyString(), anyString());
    }

    @Test
    void missingDeviceIsNotFound() {
        when(repository.findByIpv4Address(address)).thenReturn(null);
        assertStatus(HttpStatus.NOT_FOUND, () -> service.requestReboot(address));
        assertTrue(tasks.isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Kapsch", "Yunex", ""})
    void unsupportedManufacturerIsRejectedBeforeAdmission(String name) {
        manufacturer.setName(name);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestReboot(address));
        assertTrue(tasks.isEmpty());
        verifyNoInteractions(client);
        // A rejected target must not retain a per-device admission slot.
        manufacturer.setName("Commsignia");
        assertNotNull(service.requestReboot(address));
    }

    @Test
    void missingModelOrManufacturerIsRejectedBeforeAdmission() {
        var model = rsu.getModel();
        rsu.setModel(null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestReboot(address));
        rsu.setModel(model);
        model.setManufacturer(null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestReboot(address));
        assertTrue(tasks.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void manufacturerMatchingIgnoresCase() {
        manufacturer.setName("COMMSIGNIA");
        assertNotNull(service.requestReboot(address));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingOrBlankUsernameRejectsBeforeAdmission(String username) {
        credentials.setUsername(username);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestReboot(address));
        assertTrue(tasks.isEmpty());
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid\ninput", "invalid\rinput"})
    void missingOrMultilinePasswordRejectsBeforeAdmission(String password) {
        credentials.setPassword(password);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestReboot(address));
        assertTrue(tasks.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void missingCredentialsRejectBeforeAdmissionWithoutRetainingTheDevice() {
        rsu.setCredential(null);
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.requestReboot(address));
        assertTrue(tasks.isEmpty());
        verifyNoInteractions(client);
        rsu.setCredential(credentials);
        assertNotNull(service.requestReboot(address));
    }

    @ParameterizedTest
    @EnumSource(SshRebootClient.Outcome.class)
    void eachCommandOutcomeReleasesAdmissionWithoutRetrying(SshRebootClient.Outcome outcome) {
        when(client.reboot(any(), anyString(), anyString())).thenReturn(outcome);
        service.requestReboot(address);
        tasks.removeFirst().run();
        verify(client, times(1)).reboot(address, credentials.getUsername(), credentials.getPassword());
        assertTrue(tasks.isEmpty());
        assertNotNull(service.requestReboot(address));
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
}
