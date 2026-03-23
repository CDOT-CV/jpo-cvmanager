package us.dot.its.jpo.ode.api.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> checkFirmwareUpgrade(String organization, List<String> rsuIps) {
        String rsuIp = rsuIps.get(0);
        Rsu rsu = rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization);
        if (rsu == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
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
        Map<String, Object> response = new HashMap<>();

        for (String rsuIp : rsuIps) {
            if (!rsuUpgradeContextService.hasCompleteRsuData(rsuIp, organization)) {
                response.put(rsuIp, Map.of(
                        "code", 400,
                        "data", "Provided RSU IP does not have complete RSU data for organization: " + organization
                                + "::" + rsuIp));
                continue;
            }

            try {
                UpgradeExecutionResult result = markRsuForUpgrade(rsuIp, organization);
                response.put(rsuIp, Map.of(
                        "code", result.statusCode(),
                        "data", result.body()));
            } catch (ResponseStatusException ex) {
                int statusCode = ex.getStatusCode().value();
                String errorMessage = ex.getReason() == null ? "Failed to initiate firmware upgrade" : ex.getReason();
                response.put(rsuIp, Map.of(
                        "code", statusCode,
                        "data", errorMessage));
                log.warn("Firmware upgrade failed for {} with status {}: {}", rsuIp, statusCode, errorMessage);
            } catch (Exception ex) {
                response.put(rsuIp, Map.of(
                        "code", 500,
                        "data", "Failed to initiate firmware upgrade for RSU '" + rsuIp + "'"));
                log.error("Unexpected firmware upgrade error for {}", rsuIp, ex);
            }
        }

        return response;
    }

    @Transactional
    protected UpgradeExecutionResult markRsuForUpgrade(String rsuIp, String organization) {
        if (firmwareManagerEndpoint == null || firmwareManagerEndpoint.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "The firmware manager is not supported for this CV Manager deployment");
        }

        Rsu rsu = rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization);
        if (rsu == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provided RSU IP does not have complete RSU data for organization: " + organization + "::" + rsuIp);
        }

        FirmwareUpgradeInfo upgradeInfo = checkForUpgrade(rsu);

        if (!upgradeInfo.upgradeAvailable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
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
}
