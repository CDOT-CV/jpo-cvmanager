package us.dot.its.jpo.ode.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureValidationService {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Base URL for ISS signing API.
     *
     * Supports both a generic variable and the existing Linux variable name.
     */
    @Value("${issSigningApiBaseUrl:}")
    private String baseUrl;

    public Optional<String> getHealth() {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String uri = endpoint("/health");

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return Optional.ofNullable(response.getBody());
            }

            log.error("Error doing health check: {} {}", response.getStatusCode().value(), response.getBody());
        } catch (RestClientException ex) {
            log.error("Error doing health check", ex);
        }

        return Optional.empty();
    }

    public ValidateStatus validate(byte[] bytes) {
        if (!isConfigured()) {
            return ValidateStatus.UNAVAILABLE;
        }

        String uri = endpoint("/validate");
        HttpHeaders headers = jsonHeaders();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageHex", bytesToHex(bytes, false));
        body.put("shouldValidate", true);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    uri,
                    new HttpEntity<>(body, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return enumFromString(response.getBody(), ValidateStatus.class, ValidateStatus.FAILURE);
            }

            log.error("Error validating: {} {}", response.getStatusCode().value(), response.getBody());
        } catch (RestClientException ex) {
            log.error("Error validating", ex);
        }

        return ValidateStatus.FAILURE;
    }

    /**
     * Helper overload for callers that have ASN.1 data as a String.
     *
     * If the input looks like a hexadecimal payload, it is decoded as hex;
     * otherwise, the String is converted to UTF-8 bytes.
     */
    public ValidateStatus validate(String asn1Data) {
        if (asn1Data == null || asn1Data.isBlank()) {
            return ValidateStatus.FAILURE;
        }

        String normalized = asn1Data.replaceAll("\\s+", "");
        byte[] payload = isHexString(normalized)
                ? hexToBytes(normalized)
                : asn1Data.getBytes(StandardCharsets.UTF_8);

        return validate(payload);
    }

    public Optional<byte[]> sign(int psid, byte[] tbsOer, Integer jIndex, Boolean digestSigner) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String uri = endpoint("/sign");
        HttpHeaders headers = jsonHeaders();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("psid", psid);
        body.put("tbsOerHex", bytesToHex(tbsOer, true));
        body.put("jIndex", jIndex);
        body.put("digestSigner", digestSigner);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    uri,
                    new HttpEntity<>(body, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                JsonNode signedHexNode = json.get("signedMessageHex");
                if (signedHexNode == null || signedHexNode.isNull()) {
                    log.error("Error signing: missing signedMessageHex in response body");
                    return Optional.empty();
                }
                return Optional.of(hexToBytes(signedHexNode.asText()));
            }

            log.error("Error signing: {} {}", response.getStatusCode().value(), response.getBody());
        } catch (Exception ex) {
            log.error("Error signing", ex);
        }

        return Optional.empty();
    }

    public void getDeviceCerts(String token, String tokenType, String deviceId) {
        if (!isConfigured()) {
            return;
        }

        String uri = endpoint("/get-device-certs");
        HttpHeaders headers = jsonHeaders();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("tokenType", normalizeTokenType(tokenType));
        body.put("deviceId", deviceId);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    uri,
                    new HttpEntity<>(body, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Device certs request sent successfully");
                return;
            }

            log.error("Error getting device certs: {} {}", response.getStatusCode().value(), response.getBody());
        } catch (RestClientException ex) {
            log.error("Error getting device certs", ex);
        }
    }

    public SigningApiState getState() {
        if (!isConfigured()) {
            return SigningApiState.NEED_INIT;
        }

        String uri = endpoint("/state");

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                String state = json.path("state").asText(null);
                return enumFromString(state, SigningApiState.class, SigningApiState.NEED_CERTS);
            }

            log.error("Error getting state: {} {}", response.getStatusCode().value(), response.getBody());
        } catch (Exception ex) {
            log.error("Error getting state", ex);
        }

        return SigningApiState.NEED_INIT;
    }

    public void topOffCerts(String token, int tokenTypeIndex) {
        if (!isConfigured()) {
            return;
        }

        String uri = endpoint("/top-off-certs");
        HttpHeaders headers = jsonHeaders();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("tokenType", tokenTypeIndex);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    uri,
                    new HttpEntity<>(body, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Top off certs request sent successfully");
                return;
            }

            log.error("Error topping off certs: {} {}", response.getStatusCode().value(), response.getBody());
        } catch (RestClientException ex) {
            log.error("Error topping off certs", ex);
        }
    }

    public void clearCache(int clearBeforeUnixTimeSeconds) {
        if (!isConfigured()) {
            return;
        }

        String uri = endpoint("/clear-cache");
        HttpHeaders headers = jsonHeaders();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clearBeforeUnixTimeSeconds", clearBeforeUnixTimeSeconds);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    uri,
                    new HttpEntity<>(body, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Clear cache request sent successfully");
                return;
            }

            log.error("Error clearing cache: {} {}", response.getStatusCode().value(), response.getBody());
        } catch (RestClientException ex) {
            log.error("Error clearing cache", ex);
        }
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hexadecimal string");
        }

        int len = hex.length();
        byte[] result = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hexadecimal string");
            }
            result[i / 2] = (byte) ((hi << 4) + lo);
        }

        return result;
    }

    public static String bytesToHex(byte[] bytes, boolean upperCase) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }

        String hex = builder.toString();
        return upperCase ? hex.toUpperCase(Locale.ROOT) : hex;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, JSON_CONTENT_TYPE);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String endpoint(String path) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + path;
    }

    private boolean isConfigured() {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn(
                    "ISS signing API base URL is not configured. Set ISS_SIGNING_API_BASE_URL or LINUX_ISS_SIGNING_URL.");
            return false;
        }
        return true;
    }

    private String normalizeTokenType(String tokenType) {
        if (tokenType == null) {
            return null;
        }

        return tokenType.toLowerCase(Locale.ROOT).replace("_", "-");
    }

    private static <T extends Enum<T>> T enumFromString(String value, Class<T> enumClass, T defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Enum.valueOf(enumClass, value.trim());
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }

    private static boolean isHexString(String value) {
        return value != null
                && !value.isBlank()
                && value.length() % 2 == 0
                && value.matches("(?i)[0-9a-f]+");
    }

    public enum ValidateStatus {
        SUCCESS,
        FAILURE,
        UNAVAILABLE
    }

    public enum SigningApiState {
        NEED_INIT,
        NEED_CERTS,
        READY
    }
}
