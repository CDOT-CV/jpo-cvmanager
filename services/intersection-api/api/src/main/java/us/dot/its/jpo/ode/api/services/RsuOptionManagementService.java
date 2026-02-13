package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.models.devices.management.RsuPatch;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOption;
import us.dot.its.jpo.ode.api.repositories.RsuOptionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

/**
 * Service for managing RSU options including TIM deposit and SNMP monitoring settings.
 * This service handles the lifecycle of RSU option configurations independently from
 * core RSU management operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RsuOptionManagementService {

    private final RsuRepository rsuRepository;
    private final RsuOptionRepository rsuOptionRepository;

    /**
     * Modifies RSU options (tim_deposit and snmp_monitoring) for a given RSU.
     * Creates a new RsuOption entry if one doesn't exist, or updates the existing one.
     * Only saves to the database if actual changes are detected.
     *
     * @param rsuIp The IPv4 address of the RSU
     * @param rsuPatch The patch object containing the new option values
     * @throws ResponseStatusException if the RSU is not found or the IP is invalid
     */
    public void modifyRsuOption(String rsuIp, RsuPatch rsuPatch) {
        log.info("Modifying Rsu option with IP: {}", rsuIp);

        Rsu existingRsu = findRsuByIp(rsuIp);

        // Early return if no option fields are provided
        if (rsuPatch.getTimDeposit() == null && rsuPatch.getSnmpMonitoring() == null) {
            log.info("Patch does not contain tim_deposit or snmp_monitoring values, no modification necessary");
            return;
        }

        RsuOption rsuOption = getOrCreateRsuOption(existingRsu);
        boolean isNewOption = rsuOption.getId() == null;

        boolean modified = updateRsuOptionFields(rsuOption, rsuPatch, isNewOption);

        saveRsuOptionIfModified(rsuOption, modified, isNewOption);

        log.info("Done modifying Rsu option with IP: {}", rsuIp);
    }

    /**
     * Finds an RSU by its IPv4 address.
     *
     * @param rsuIp The IPv4 address string
     * @return The RSU entity
     * @throws ResponseStatusException if the IP is invalid or RSU not found
     */
    private Rsu findRsuByIp(String rsuIp) {
        try {
            InetAddress inetAddress = InetAddress.getByName(rsuIp);
            Rsu rsu = rsuRepository.findByIpv4Address(inetAddress);

            if (rsu == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RSU not found with IP: " + rsuIp);
            }

            return rsu;
        } catch (UnknownHostException e) {
            log.error("Invalid IP address: {}", rsuIp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IP address: " + rsuIp, e);
        }
    }

    /**
     * Retrieves an existing RsuOption for the given RSU or creates a new one if it doesn't exist.
     *
     * @param rsu The RSU entity
     * @return The existing or newly created RsuOption
     */
    private RsuOption getOrCreateRsuOption(Rsu rsu) {
        Optional<RsuOption> rsuOptionOptional = rsuOptionRepository.findByRsuId(rsu.getId());

        if (rsuOptionOptional.isPresent()) {
            log.info("Found existing rsu_option for RSU with ID: {}", rsu.getId());
            return rsuOptionOptional.get();
        } else {
            log.info("Creating new rsu_option for RSU with ID: {}", rsu.getId());
            RsuOption newOption = new RsuOption();
            newOption.setRsu(rsu);
            return newOption;
        }
    }

    /**
     * Updates the RSU option fields based on the patch values.
     *
     * @param rsuOption The RsuOption to update
     * @param rsuPatch The patch containing new values
     * @param isNewOption Whether this is a new option being created
     * @return true if any changes were made, false otherwise
     */
    private boolean updateRsuOptionFields(RsuOption rsuOption, RsuPatch rsuPatch, boolean isNewOption) {
        boolean modified = false;

        modified |= updateTimDepositField(rsuOption, rsuPatch.getTimDeposit(), isNewOption);
        modified |= updateSnmpMonitoringField(rsuOption, rsuPatch.getSnmpMonitoring(), isNewOption);

        return modified;
    }

    /**
     * Updates the tim_deposit field if a new value is provided and different from current.
     *
     * @param rsuOption The RsuOption to update
     * @param proposedValue The proposed new value (may be null)
     * @param isNewOption Whether this is a new option being created
     * @return true if the field was modified, false otherwise
     */
    private boolean updateTimDepositField(RsuOption rsuOption, Boolean proposedValue, boolean isNewOption) {
        if (proposedValue == null) {
            return false;
        }

        log.info("Proposed tim_deposit value: {}", proposedValue);

        if (isNewOption || !rsuOption.getTimDeposit().equals(proposedValue)) {
            if (!isNewOption) {
                log.info("Current tim_deposit value: {}, changing to: {}",
                        rsuOption.getTimDeposit(), proposedValue);
            }
            rsuOption.setTimDeposit(proposedValue);
            return true;
        } else {
            log.info("tim_deposit value unchanged: {}", proposedValue);
            return false;
        }
    }

    /**
     * Updates the snmp_monitoring field if a new value is provided and different from current.
     *
     * @param rsuOption The RsuOption to update
     * @param proposedValue The proposed new value (may be null)
     * @param isNewOption Whether this is a new option being created
     * @return true if the field was modified, false otherwise
     */
    private boolean updateSnmpMonitoringField(RsuOption rsuOption, Boolean proposedValue, boolean isNewOption) {
        if (proposedValue == null) {
            return false;
        }

        log.info("Proposed snmp_monitoring value: {}", proposedValue);

        if (isNewOption || !rsuOption.getSnmpMonitoring().equals(proposedValue)) {
            if (!isNewOption) {
                log.info("Current snmp_monitoring value: {}, changing to: {}",
                        rsuOption.getSnmpMonitoring(), proposedValue);
            }
            rsuOption.setSnmpMonitoring(proposedValue);
            return true;
        } else {
            log.info("snmp_monitoring value unchanged: {}", proposedValue);
            return false;
        }
    }

    /**
     * Saves the RsuOption to the database if modifications were detected.
     *
     * @param rsuOption The RsuOption to save
     * @param modified Whether any modifications were made
     * @param isNewOption Whether this is a new option being created
     */
    private void saveRsuOptionIfModified(RsuOption rsuOption, boolean modified, boolean isNewOption) {
        if (modified) {
            log.info("Saving {} rsu_option entry - tim_deposit: {}, snmp_monitoring: {}",
                    isNewOption ? "new" : "modified",
                    rsuOption.getTimDeposit(),
                    rsuOption.getSnmpMonitoring());
            rsuOptionRepository.save(rsuOption);
        } else {
            log.info("No changes detected, skipping save");
        }
    }
}

