package us.dot.its.jpo.ode.api.accessors.firmware;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareRule;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareFile;

import java.util.List;

@Repository
public interface FirmwareRuleRepository extends JpaRepository<FirmwareRule, Integer> {

    /**
     * Find all rules for a specific firmware file (toFirmware)
     * 
     * @param toFirmware Target firmware file
     * @return List of firmware rules
     */
    List<FirmwareRule> findByToFirmware(FirmwareFile toFirmware);

    /**
     * Find rules by from firmware
     * 
     * @param fromFirmware Source firmware file
     * @return List of firmware rules
     */
    List<FirmwareRule> findByFromFirmware(FirmwareFile fromFirmware);

    /**
     * Delete all rules for a specific firmware file (toFirmware)
     * 
     * @param toFirmware Target firmware file
     */
    @Modifying
    @Query("DELETE FROM FirmwareRule r WHERE r.toFirmware = :toFirmware")
    void deleteByToFirmware(@Param("toFirmware") FirmwareFile toFirmware);

    /**
     * Count rules for a specific firmware file (toFirmware)
     * 
     * @param toFirmware Target firmware file
     * @return Count of rules
     */
    long countByToFirmware(FirmwareFile toFirmware);

    /**
     * Find rules by device type through firmware file
     * 
     * @param deviceType RSU or OBU
     * @return List of firmware rules
     */
    @Query("SELECT r FROM FirmwareRule r JOIN r.toFirmware f WHERE f.deviceType = :deviceType")
    List<FirmwareRule> findByDeviceType(@Param("deviceType") String deviceType);
}
