package us.dot.its.jpo.ode.api.services.ssh;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSession.ClientSessionEvent;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.core.CoreModuleProperties;
import us.dot.its.jpo.ode.api.config.RsuRebootProperties;

/** Reuses SSH infrastructure while keeping credentials and channels local to each reboot. */
public final class MinaSshRebootClient implements SshRebootClient {
    // Embedded RSUs may provide a root login without installing sudo. Test the
    // effective UID on the device rather than assume privilege from the username.
    static final String COMMAND = "if [ \"$(id -u)\" = 0 ]; then reboot; else sudo -S -p '' reboot; fi";

    private final SshClient client;
    private final RsuRebootProperties properties;

    public MinaSshRebootClient(RsuRebootProperties properties) {
        this.properties = properties;
        client = SshClient.setUpDefaultClient();
        CoreModuleProperties.NIO_WORKERS.set(client, Math.min(4, properties.getConcurrency()));
        client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);

        // Match the previous Fabric behavior: devices use database passwords, without
        // pinning server keys. Unknown or changed keys do not block a legitimate reimage.
        // This deliberately does not verify the SSH server's identity.
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.start();
    }

    @Override
    public Outcome reboot(InetAddress address, String username, String password) {
        // Share one deadline across all SSH phases rather than restarting the timeout at each phase.
        long deadline = System.nanoTime() + properties.getTimeout().toNanos();
        ClientSession session = null;
        ChannelExec channel = null;
        Outcome failure = Outcome.CONNECT_FAILED;
        boolean dispatched = false;

        try {
            var connection = client.connect(username, address.getHostAddress(), properties.getPort());
            try {
                session = connection.verify(remaining(deadline)).getSession();
            } catch (IOException e) {
                connection.cancel();
                throw e;
            }

            // Wait for key exchange before authenticating with the database password.
            if (!session.waitFor(EnumSet.of(ClientSessionEvent.WAIT_AUTH), remaining(deadline))
                    .contains(ClientSessionEvent.WAIT_AUTH)) {
                return Outcome.CONNECT_FAILED;
            }

            failure = Outcome.AUTHENTICATION_FAILED;
            session.addPasswordIdentity(password);
            session.auth().verify(remaining(deadline));

            channel = session.createExecChannel(COMMAND);
            // Discard remote output: sudo or device banners must not reach logs or grow buffers.
            channel.setOut(OutputStream.nullOutputStream());
            channel.setErr(OutputStream.nullOutputStream());

            // Once execution is requested, a lost reply cannot safely be treated as a failed command.
            failure = Outcome.UNKNOWN;
            channel.open().verify(remaining(deadline));
            dispatched = true;

            byte[] input = (password + "\n").getBytes(StandardCharsets.UTF_8);
            try {
                channel.getInvertedIn().write(input);
                channel.getInvertedIn().flush();
                // Only one sudo password attempt; EOF prevents another prompt from hanging.
                channel.getInvertedIn().close();
            } finally {
                Arrays.fill(input, (byte) 0);
            }

            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), remaining(deadline));
            return commandOutcome(channel);
        } catch (IOException e) {
            if (channel != null && channel.getExitStatus() != null) return commandOutcome(channel);

            // A reboot may close the socket before an exit status arrives. This is
            // expected on some devices, but it does not prove that they restarted.
            if (dispatched && (channel.isClosing() || session.isClosing())) return Outcome.DISCONNECTED;
            return failure;
        } finally {
            if (channel != null) channel.close(true);
            if (session != null) session.close(true);
        }
    }

    private static Outcome commandOutcome(ChannelExec channel) {
        Integer status = channel.getExitStatus();

        // CLOSED can be reported while asynchronous channel cleanup is still finishing.
        if (status == null) return channel.isClosing() ? Outcome.DISCONNECTED : Outcome.UNKNOWN;

        return status == 0 ? Outcome.COMMAND_ACCEPTED : Outcome.COMMAND_FAILED;
    }

    private static Duration remaining(long deadline) throws IOException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0 || Thread.currentThread().isInterrupted()) {
            throw new IOException("SSH reboot deadline expired or execution interrupted");
        }

        return Duration.ofNanos(nanos);
    }

    @Override
    public void close() {
        client.stop();
    }
}
