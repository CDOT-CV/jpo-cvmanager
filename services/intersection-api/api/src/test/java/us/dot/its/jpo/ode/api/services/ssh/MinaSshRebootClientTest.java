package us.dot.its.jpo.ode.api.services.ssh;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import us.dot.its.jpo.ode.api.config.RsuRebootProperties;

/** Real SSH exchanges on loopback; the test server records commands and never runs them. */
@Timeout(15)
class MinaSshRebootClientTest {
    private SshServer server;
    private KeyPair key;
    private RsuRebootProperties properties;
    private final String password = UUID.randomUUID().toString();
    private final AtomicInteger commands = new AtomicInteger();
    private final List<Thread> workers = new CopyOnWriteArrayList<>();
    private volatile String receivedCommand;
    private volatile String receivedPassword;
    private volatile int exitStatus;
    private volatile boolean disconnect;
    private volatile boolean hang;

    @BeforeEach
    void startAgent() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        key = generator.generateKeyPair();
        startServer(0);
        properties = new RsuRebootProperties();
        properties.setPort(server.getPort());
        properties.setTimeout(Duration.ofSeconds(5));
    }

    private void startServer(int port) throws Exception {
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(port);
        CoreModuleProperties.NIO_WORKERS.set(server, 2);
        server.setKeyPairProvider(KeyPairProvider.wrap(key));
        server.setPasswordAuthenticator((user, supplied, session) -> user.equals("test-user") && password.equals(supplied));
        server.setCommandFactory((channel, command) -> {
            receivedCommand = command;
            commands.incrementAndGet();
            return new TestCommand();
        });
        server.start();
    }

    @AfterEach
    void stopAgent() throws Exception {
        for (Thread worker : workers) worker.interrupt();
        server.stop(true);
        for (Thread worker : workers) worker.join(1000);
    }

    @Test
    void sendsFixedCommandAndSudoPasswordOverInput() throws Exception {
        assertEquals(SshRebootClient.Outcome.COMMAND_ACCEPTED, execute(password));
        assertEquals("if [ \"$(id -u)\" = 0 ]; then reboot; else sudo -S -p '' reboot; fi", receivedCommand);
        assertEquals(password, receivedPassword);
        assertFalse(receivedCommand.contains(password));
        assertEquals(1, commands.get());
    }

    @Test
    void acceptsUnknownHostWithoutAKeyFile() throws Exception {
        assertEquals(SshRebootClient.Outcome.COMMAND_ACCEPTED, execute(password));
        assertEquals(1, commands.get());
    }

    @Test
    void acceptsChangedHostKeyAfterADeviceReplacement() throws Exception {
        int port = server.getPort();
        try (var client = new MinaSshRebootClient(properties)) {
            var address = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            assertEquals(SshRebootClient.Outcome.COMMAND_ACCEPTED, client.reboot(address, "test-user", password));
            server.stop(true);
            key = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            startServer(port);
            assertEquals(SshRebootClient.Outcome.COMMAND_ACCEPTED, client.reboot(address, "test-user", password));
        }
        assertEquals(2, commands.get());
    }

    @Test
    void rejectsWrongPasswordWithoutSendingCommand() throws Exception {
        assertEquals(SshRebootClient.Outcome.AUTHENTICATION_FAILED, execute(UUID.randomUUID().toString()));
        assertEquals(0, commands.get());
    }

    @Test
    void nonzeroExitIsDefiniteFailure() throws Exception {
        exitStatus = 1;
        assertEquals(SshRebootClient.Outcome.COMMAND_FAILED, execute(password));
    }

    @Test
    void droppedConnectionAfterDispatchIsRecordedAndNeverRetried() throws Exception {
        disconnect = true;
        assertEquals(SshRebootClient.Outcome.DISCONNECTED, execute(password));
        assertEquals(1, commands.get());
    }

    @Test
    void commandThatNeverReturnsHasBoundedDuration() throws Exception {
        hang = true;
        properties.setTimeout(Duration.ofSeconds(1));
        long started = System.nanoTime();
        assertEquals(SshRebootClient.Outcome.UNKNOWN, execute(password));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(4)) < 0);
        assertEquals(1, commands.get());
    }

    @Test
    void repeatedCallsUseSeparateSessions() throws Exception {
        try (var client = new MinaSshRebootClient(properties)) {
            var address = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            assertEquals(SshRebootClient.Outcome.COMMAND_ACCEPTED, client.reboot(address, "test-user", password));
            assertEquals(SshRebootClient.Outcome.AUTHENTICATION_FAILED,
                    client.reboot(address, "test-user", UUID.randomUUID().toString()));
            assertEquals(SshRebootClient.Outcome.COMMAND_ACCEPTED, client.reboot(address, "test-user", password));
        }
        assertEquals(2, commands.get());
    }

    private SshRebootClient.Outcome execute(String suppliedPassword) throws Exception {
        try (var client = new MinaSshRebootClient(properties)) {
            return client.reboot(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), "test-user", suppliedPassword);
        }
    }

    private class TestCommand implements Command {
        private InputStream input;
        private ExitCallback exit;
        private Thread worker;

        @Override public void setInputStream(InputStream stream) { input = stream; }
        @Override public void setOutputStream(OutputStream stream) { }
        @Override public void setErrorStream(OutputStream stream) { }
        @Override public void setExitCallback(ExitCallback callback) { exit = callback; }

        @Override
        public void start(ChannelSession channel, Environment environment) {
            worker = Thread.ofPlatform().daemon().start(() -> {
                try {
                    receivedPassword = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).readLine();
                    if (hang) {
                        Thread.sleep(10_000);
                    } else if (disconnect) {
                        channel.getSession().close(true);
                    } else {
                        exit.onExit(exitStatus);
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                }
            });
            workers.add(worker);
        }

        @Override
        public void destroy(ChannelSession channel) {
            if (worker != null) worker.interrupt();
        }
    }
}
