package us.dot.its.jpo.snmp.client;

import java.util.Objects;

/**
 * Immutable SNMPv3 authPriv credentials supplied for each target.
 * Different full profiles have isolated security state, even when usernames match.
 * {@code toString()} is redacted; password accessors return the actual secrets.
 */
public final class SnmpCredentials {
    /** Supported authentication algorithms; SHA is HMAC-SHA-1. */
    public enum Authentication { SHA, SHA_256, SHA_512 }

    /** Supported encryption algorithms; AES means AES-128. */
    public enum Privacy { AES, DES }
    private final String username;
    private final String authenticationPassword;
    private final String privacyPassword;
    private final Authentication authentication;
    private final Privacy privacy;

    /**
     * Creates a SHA/AES-128 profile. A null or empty privacy password uses the authentication password.
     *
     * @param username device username
     * @param authenticationPassword authentication passphrase, at least eight characters
     * @param privacyPassword encryption passphrase, or null/empty to reuse the authentication passphrase
     * @throws IllegalArgumentException if a required value is empty, contains control characters,
     *         or a passphrase has fewer than eight characters
     */
    public SnmpCredentials(String username, String authenticationPassword, String privacyPassword) {
        this(username, authenticationPassword, privacyPassword, Authentication.SHA, Privacy.AES);
    }

    /**
     * Creates a profile with explicit algorithms and the same privacy-password fallback.
     *
     * @param username device username
     * @param authenticationPassword authentication passphrase, at least eight characters
     * @param privacyPassword encryption passphrase, or null/empty to reuse authentication
     * @param authentication authentication algorithm
     * @param privacy encryption algorithm
     * @throws IllegalArgumentException if credential text is invalid or passphrases are too short
     * @throws NullPointerException if either algorithm is null
     */
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

    /**
     * Returns the authentication secret for transport setup. Do not include it in application logs.
     * @return authentication passphrase
     */
    public String authenticationPassword() { return authenticationPassword; }

    /**
     * Returns the effective encryption secret after applying the fallback.
     * @return encryption passphrase
     */
    public String privacyPassword() { return privacyPassword; }
    public Authentication authentication() { return authentication; }
    public Privacy privacy() { return privacy; }

    /** Compares the full effective profile, including secrets and algorithms. */
    @Override public boolean equals(Object other) {
        return other instanceof SnmpCredentials that && username.equals(that.username)
                && authenticationPassword.equals(that.authenticationPassword)
                && privacyPassword.equals(that.privacyPassword)
                && authentication == that.authentication && privacy == that.privacy;
    }

    @Override public int hashCode() {
        return Objects.hash(username, authenticationPassword, privacyPassword, authentication, privacy);
    }

    @Override public String toString() { return "SnmpCredentials[REDACTED]"; }
}
