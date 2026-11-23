package us.dot.its.jpo.ode.api.services.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSession.ClientSessionEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import us.dot.its.jpo.ode.api.config.RsuRebootProperties;

/** Deterministic failures after command dispatch; real SSH exchanges have separate loopback tests. */
class MinaSshRebootClientFailureTest {
    private static Stream<Arguments> exitStatuses() {
        return Stream.of(
                Arguments.of(0, SshRebootClient.Outcome.COMMAND_ACCEPTED),
                Arguments.of(1, SshRebootClient.Outcome.COMMAND_FAILED),
                Arguments.of(null, SshRebootClient.Outcome.UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("exitStatuses")
    void receivedExitStatusSurvivesALaterInputFailure(Integer status, SshRebootClient.Outcome expected) throws IOException {
        var ssh = mock(SshClient.class);
        var session = mock(ClientSession.class);
        var channel = mock(ChannelExec.class);
        var connection = mock(ConnectFuture.class);
        var authentication = mock(AuthFuture.class);
        var opening = mock(OpenFuture.class);
        var input = mock(OutputStream.class);
        var receivedStatus = new AtomicReference<Integer>();
        when(ssh.getProperties()).thenReturn(new HashMap<>());
        when(ssh.connect(anyString(), anyString(), anyInt())).thenReturn(connection);
        when(connection.verify(any(Duration.class))).thenReturn(connection);
        when(connection.getSession()).thenReturn(session);
        when(session.waitFor(any(), any(Duration.class))).thenReturn(EnumSet.of(ClientSessionEvent.WAIT_AUTH));
        when(session.auth()).thenReturn(authentication);
        when(authentication.verify(any(Duration.class))).thenReturn(authentication);
        when(session.createExecChannel(MinaSshRebootClient.COMMAND)).thenReturn(channel);
        when(channel.open()).thenReturn(opening);
        when(opening.verify(any(Duration.class))).thenReturn(opening);
        when(channel.getInvertedIn()).thenReturn(input);
        when(channel.getExitStatus()).thenAnswer(ignored -> receivedStatus.get());
        doAnswer(ignored -> {
            // Publish the remote outcome before the subsequent local I/O error.
            receivedStatus.set(status);
            throw new IOException("Channel input closed");
        }).when(input).write(any(byte[].class));

        var address = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        try (var client = new MinaSshRebootClient(new RsuRebootProperties(), ssh)) {
            assertEquals(expected, client.reboot(address, "fixture-user", UUID.randomUUID().toString()));
        }

        verify(ssh, times(1)).connect(anyString(), anyString(), anyInt());
        verify(session, times(1)).createExecChannel(MinaSshRebootClient.COMMAND);
        verify(channel).close(true);
        verify(session).close(true);
        verify(ssh).stop();
    }
}
