package us.dot.its.jpo.ode.api.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.ParameterizedTypeReference;

import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeCheckResponseDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeResultDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeStartResponseDto;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

    public FirmwareUpgradeCheckResponseDto checkFirmwareUpgrade(String organization, String rsuIp) {
        Rsu rsu = rsuUpgradeContextService.findRsuForOrganization(rsuIp, organization);
        if (rsu == null) {
            throw new EntityNotFoundException(
                    "Provided RSU IP does not have complete RSU data for organization: " + organization + "::" + rsuIp);
        }

        FirmwareUpgradeInfo upgradeInfo = checkForUpgrade(rsu);
        FirmwareImage upgradeImage = upgradeInfo.upgradeImage();

        return new FirmwareUpgradeCheckResponseDto(
                upgradeInfo.upgradeAvailable(),
                upgradeImage != null && upgradeImage.getId() != null ? upgradeImage.getId().longValue() : -1L,
                upgradeImage != null && upgradeImage.getName() != null ? upgradeImage.getName() : "",
                upgradeImage != null && upgradeImage.getVersion() != null ? upgradeImage.getVersion() : "");
    }

    public FirmwareUpgradeStartResponseDto startFirmwareUpgradeForRsus(String organization, List<String> rsuIps) {
        Map<String, FirmwareUpgradeResultDto> results = new LinkedHashMap<>();

        for (String rsuIp : rsuIps) {
            if (!rsuUpgradeContextService.hasCompleteRsuData(rsuIp, organization)) {
                results.put(rsuIp, createUpgradeResult(
                        HttpStatus.NOT_FOUND.value(),
                        "Provided RSU IP does not have complete RSU data for organization: " + organization + "::"
                                + rsuIp));
                continue;
            }

            try {
                UpgradeExecutionResult result = executeUpgradeForRsu(rsuIp, organization);
                results.put(rsuIp, createUpgradeResult(result.statusCode(), result.body()));
            } catch (EntityNotFoundException ex) {
                results.put(rsuIp, createUpgradeResult(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
            } catch (FirmwareUpgradeUnavailableException ex) {
                results.put(rsuIp, createUpgradeResult(HttpStatus.CONFLICT.value(), ex.getMessage()));
            } catch (ResponseStatusException ex) {
                results.put(rsuIp, createUpgradeResult(
                        ex.getStatusCode().value(),
                        ex.getReason() == null || ex.getReason().isBlank() ? ex.getMessage() : ex.getReason()));
            } catch (RuntimeException ex) {
                log.warn("Failed to start firmware upgrade for RSU {}", rsuIp, ex);
                results.put(rsuIp, createUpgradeResult(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ex.getMessage() == null || ex.getMessage().isBlank()
                                ? "Failed to initiate firmware upgrade for RSU '" + rsuIp + "'"
                                : ex.getMessage()));
            }
        }

        return new FirmwareUpgradeStartResponseDto(results);
    }

    protected UpgradeExecutionResult executeUpgradeForRsu(String rsuIp, String organization) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return Objects.requireNonNull(
                transactionTemplate.execute(_ -> markRsuForUpgrade(rsuIp, organization)),
                "Upgrade execution result must not be null");
    }

    private FirmwareUpgradeResultDto createUpgradeResult(int statusCode, String message) {
        return new FirmwareUpgradeResultDto(statusCode, message == null ? "" : message);
    }

    private FirmwareUpgradeResultDto createUpgradeResult(int statusCode, Map<String, Object> data) {
        return new FirmwareUpgradeResultDto(statusCode, data);
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
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    firmwareManagerEndpoint + "/init_firmware_upgrade",
                    HttpMethod.POST,
                    new HttpEntity<>(postBody, headers),
                    new ParameterizedTypeReference<>() {});

            Map<String, Object> responseBody = response.getBody() != null ? response.getBody() : Map.of();
            log.info("Firmware manager response for {}: {}", rsuIp, responseBody);
            return new UpgradeExecutionResult(responseBody, response.getStatusCode().value());
        } catch (HttpStatusCodeException ex) {
            String errorMessage = ex.getResponseBodyAsString();
            if (errorMessage.isBlank()) {
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

    public record UpgradeExecutionResult(Map<String, Object> body, int statusCode) {
    }

    public record FirmwareUpgradeInfo(boolean upgradeAvailable, FirmwareImage upgradeImage) {
    }

    public static class FirmwareUpgradeUnavailableException extends RuntimeException {
        public FirmwareUpgradeUnavailableException(String message) {
            super(message);
        }
    }
}
