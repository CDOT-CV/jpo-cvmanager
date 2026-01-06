package us.dot.its.jpo.ode.api.emails;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;

import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UnsubscribeTokenGenerator {
    @Autowired
    private EmailProperties emailProperties;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String kcIssuerUri;

    public String generateUnsubscribeUrl(String emailAddress) { // Generate the token
        String token = generateUnsubscribeToken(emailAddress);

        // Encode the token to ensure it is URL-safe
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);

        // Build the unsubscribe URL
        return String.format("%s/unsubscribe?token=%s", emailProperties.getCvmgrFrontEndUri(), encodedToken);
    }

    /**
     * Generates a signed JWT token for email un-subscribe.
     *
     * @param email  The email address of the user.
     * @param expiry The expiration time in milliseconds.
     * @return The signed JWT token as a String.
     * @throws JOSEException If there is an error during signing.
     */
    public String generateUnsubscribeToken(String email) {
        // Create the JWT claims
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(kcIssuerUri) // Set the issuer
                .subject(email) // Set the subject (email address)
                .claim("purpose", "unsubscribe") // Add the purpose claim
                .issueTime(new Date()) // Set the issue time
                .build();

        // Create the HMAC signer with the secret key
        JWSSigner signer;
        try {
            signer = new MACSigner(emailProperties.getUnsubscribeSecretKey());
        } catch (KeyLengthException e) {
            log.error("Invalid key length for unsubscribe secret key", e);
            return null;
        }

        // Create the signed JWT
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256), // Specify the signing algorithm
                claimsSet);

        // Sign the JWT
        try {
            signedJWT.sign(signer);
        } catch (JOSEException e) {
            log.error("Error signing the JWT for unsubscribe token", e);
            return null;
        }

        // Return the serialized token
        return signedJWT.serialize();
    }

    /**
     * Parses and validates the unsubscribe token, returning the email address if
     * valid.
     *
     * @param token The JWT token to parse and validate.
     * @return The email address if the token is valid, null otherwise.
     */
    public String parseAndValidateToken(String token) {
        try {
            // Parse the JWT token
            SignedJWT signedJWT = SignedJWT.parse(token);

            // Create a verifier with the secret key
            JWSVerifier verifier = new MACVerifier(emailProperties.getUnsubscribeSecretKey());

            // Verify the signature
            if (!signedJWT.verify(verifier)) {
                return null; // Signature verification failed
            }

            // Get the claims set
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

            // Validate the claims (e.g., check expiration, issuer, purpose)
            Date now = new Date();
            if (claimsSet.getExpirationTime() != null && claimsSet.getExpirationTime().before(now)) {
                return null; // Token is expired
            }
            if (!claimsSet.getIssuer().equals(kcIssuerUri)) {
                return null; // Invalid issuer
            }
            if (!"unsubscribe".equals(claimsSet.getStringClaim("purpose"))) {
                return null; // Invalid purpose
            }

            // Return the subject (email address) if all validations pass
            return claimsSet.getSubject();
        } catch (Exception e) {
            return null; // Exception occurred, return null
        }
    }
}