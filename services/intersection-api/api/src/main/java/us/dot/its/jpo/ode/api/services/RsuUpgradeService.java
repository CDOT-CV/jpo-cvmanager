package us.dot.its.jpo.ode.api.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareImage;
import us.dot.its.jpo.ode.api.models.postgres.tables.FirmwareUpgradeRule;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.repositories.FirmwareUpgradeRuleRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RsuUpgradeService {

    private final RsuUpgradeContextService rsuUpgradeContextService;
    private final FirmwareUpgradeRuleRepository firmwareUpgradeRuleRepository;
    private final RsuRepository rsuRepository;

    @Value("${firmwareManagerEndpoint:}")
    private String firmwareManagerEndpoint;

    private final RestTemplate restTemplate;
    private final PlatformTransactionManager transactionManager;

    public Map<String, Object> checkFirmwareUpgrade(String organization, String rsuIp) {
        Rsu rsu = rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization);
        if (rsu == null) {
            throw new EntityNotFoundException(
                    "Provided RSU IP does not have complete RSU data for organization: " + organization + "::" + rsuIp);
        }

        FirmwareUpgradeInfo upgradeInfo = checkForUpgrade(rsu);
        FirmwareImage upgradeImage = upgradeInfo.upgradeImage();

        return Map.of(
                "upgrade_available", upgradeInfo.upgradeAvailable(),
                "upgrade_id", upgradeImage != null && upgradeImage.getId() != null ? upgradeImage.getId() : -1,
                "upgrade_name", upgradeImage != null && upgradeImage.getName() != null ? upgradeImage.getName() : "",
                "upgrade_version",
                upgradeImage != null && upgradeImage.getVersion() != null ? upgradeImage.getVersion() : "");
    }

    public Map<String, Object> startFirmwareUpgradeForRsus(String organization, List<String> rsuIps) {
        Map<String, Object> response = new LinkedHashMap<>();

        for (String rsuIp : rsuIps) {
            if (!rsuUpgradeContextService.hasCompleteRsuData(rsuIp, organization)) {
                response.put(rsuIp, createUpgradeResult(
                        HttpStatus.NOT_FOUND.value(),
                        "Provided RSU IP does not have complete RSU data for organization: " + organization + "::"
                                + rsuIp));
                continue;
            }

            try {
                UpgradeExecutionResult result = executeUpgradeForRsu(rsuIp, organization);
                response.put(rsuIp, createUpgradeResult(result.statusCode(), result.body()));
            } catch (EntityNotFoundException ex) {
                response.put(rsuIp, createUpgradeResult(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
            } catch (FirmwareUpgradeUnavailableException ex) {
                response.put(rsuIp, createUpgradeResult(HttpStatus.CONFLICT.value(), ex.getMessage()));
            } catch (ResponseStatusException ex) {
                response.put(rsuIp, createUpgradeResult(
                        ex.getStatusCode().value(),
                        ex.getReason() == null || ex.getReason().isBlank() ? ex.getMessage() : ex.getReason()));
            } catch (RuntimeException ex) {
                log.warn("Failed to start firmware upgrade for RSU {}", rsuIp, ex);
                response.put(rsuIp, createUpgradeResult(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ex.getMessage() == null || ex.getMessage().isBlank()
                                ? "Failed to initiate firmware upgrade for RSU '" + rsuIp + "'"
                                : ex.getMessage()));
            }
        }

        return response;
    }

    protected UpgradeExecutionResult executeUpgradeForRsu(String rsuIp, String organization) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return Objects.requireNonNull(
                transactionTemplate.execute(status -> markRsuForUpgrade(rsuIp, organization)),
                "Upgrade execution result must not be null");
    }

    private Map<String, Object> createUpgradeResult(int statusCode, Object data) {
        return Map.of(
                "code", statusCode,
                "data", data == null ? "" : data);
    }

    protected UpgradeExecutionResult markRsuForUpgrade(String rsuIp, String organization) {
        if (firmwareManagerEndpoint == null || firmwareManagerEndpoint.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "The firmware manager is not supported for this CV Manager deployment");
        }

        Rsu rsu = rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization);
        if (rsu == null) {
            throw new EntityNotFoundException(
                    "Provided RSU IP does not have complete RSU data for organization: " + organization + "::" + rsuIp);
        }

        FirmwareUpgradeInfo upgradeInfo = checkForUpgrade(rsu);

        if (!upgradeInfo.upgradeAvailable()) {
            throw new FirmwareUpgradeUnavailableException(
                    "Requested RSU '" + rsuIp + "' is already up to date with the latest firmware");
        }

        rsu.setTargetFirmwareVersion(upgradeInfo.upgradeImage());
        rsuRepository.save(rsu);

        try {
            Map<String, String> postBody = Map.of("rsu_ip", rsuIp);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    firmwareManagerEndpoint + "/init_firmware_upgrade",
                    new HttpEntity<>(postBody, headers),
                    Map.class);

            Object responseBody = response.getBody() == null ? Map.of() : response.getBody();
            log.info("Firmware manager response for {}: {}", rsuIp, responseBody);
            return new UpgradeExecutionResult(responseBody, response.getStatusCode().value());
        } catch (HttpStatusCodeException ex) {
            String errorMessage = ex.getResponseBodyAsString();
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "Firmware manager returned " + ex.getStatusCode().value();
            }
            throw new ResponseStatusException(ex.getStatusCode(), errorMessage, ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to initiate firmware upgrade for RSU '" + rsuIp + "'", ex);
        }
    }

    protected FirmwareUpgradeInfo checkForUpgrade(Rsu rsu) {
        FirmwareImage currentFirmware = rsu.getFirmwareVersion();

        if (currentFirmware == null || currentFirmware.getId() == null) {
            return new FirmwareUpgradeInfo(false, null);
        }

        FirmwareUpgradeRule upgradeRule = firmwareUpgradeRuleRepository
                .findFirstByFrom_Id(currentFirmware.getId())
                .orElse(null);

        if (upgradeRule == null) {
            return new FirmwareUpgradeInfo(false, null);
        }

        return new FirmwareUpgradeInfo(true, upgradeRule.getTo());
    }

    public record UpgradeExecutionResult(Object body, int statusCode) {
    }

    public record FirmwareUpgradeInfo(boolean upgradeAvailable, FirmwareImage upgradeImage) {
    }

    public static class FirmwareUpgradeUnavailableException extends RuntimeException {
        public FirmwareUpgradeUnavailableException(String message) {
            super(message);
        }
    }
}
