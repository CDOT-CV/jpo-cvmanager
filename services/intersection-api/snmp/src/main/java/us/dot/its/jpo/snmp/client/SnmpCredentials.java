package us.dot.its.jpo.snmp.client;

import java.util.Objects;

/** SNMPv3 authPriv. Intentionally excludes secrets from toString(). */
public final class SnmpCredentials {
    public enum Authentication { SHA, SHA_256, SHA_512 }
    public enum Privacy { AES, DES }
    private final String username;
    private final String authenticationPassword;
    private final String privacyPassword;
    private final Authentication authentication;
    private final Privacy privacy;

    public SnmpCredentials(String username, String authenticationPassword, String privacyPassword) {
        this(username, authenticationPassword, privacyPassword, Authentication.SHA, Privacy.AES);
    }

    public SnmpCredentials(String username, String authenticationPassword, String privacyPassword,
            Authentication authentication, Privacy privacy) {
        this.username = validate(username);
        this.authenticationPassword = validate(authenticationPassword);
        this.privacyPassword = validate(privacyPassword == null || privacyPassword.isEmpty() ? authenticationPassword : privacyPassword);
        if (this.authenticationPassword.length() < 8 || this.privacyPassword.length() < 8) {
            throw new IllegalArgumentException("SNMPv3 passphrases must contain at least eight characters");
        }
        this.authentication = Objects.requireNonNull(authentication);
        this.privacy = Objects.requireNonNull(privacy);
    }

    private static String validate(String value) {
        if (value == null || value.isEmpty() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Empty or control character in SNMP credential");
        }
        return value;
    }
    public String username() { return username; }
    public String authenticationPassword() { return authenticationPassword; }
    public String privacyPassword() { return privacyPassword; }
    public Authentication authentication() { return authentication; }
    public Privacy privacy() { return privacy; }
    @Override public String toString() { return "SnmpCredentials[REDACTED]"; }
}
