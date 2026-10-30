package us.dot.its.jpo.ode.api.services.ssh;

import java.net.InetAddress;

/** Executes only the fixed reboot command, using sudo when needed; implementations must not retry it. */
public interface SshRebootClient extends AutoCloseable {
    /** Returns the command outcome, without waiting for the device to boot again. */
    Outcome reboot(InetAddress address, String username, String password);

    @Override
    void close();

    /** A disconnect after dispatch is expected on some devices, but does not verify a restart. */
    enum Outcome {
        COMMAND_ACCEPTED, DISCONNECTED, CONNECT_FAILED, AUTHENTICATION_FAILED, COMMAND_FAILED, UNKNOWN
    }
}
