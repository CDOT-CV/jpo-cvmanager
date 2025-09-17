package us.dot.its.jpo.ode.api.accessors.firmware;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import us.dot.its.jpo.ode.api.models.postgres.tables.ObuOtaRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ObuFirmwareStatusRepository extends JpaRepository<ObuOtaRequest, Integer> {

    /**
     * Find OBU OTA requests by OBU serial number
     * 
     * @param obuSn OBU serial number
     * @return List of OTA requests for the specified OBU
     */
    List<ObuOtaRequest> findByObuSn(String obuSn);

    /**
     * Find OBU OTA requests by current firmware version
     * 
     * @param obuFirmwareVersion Current OBU firmware version
     * @return List of OTA requests with the specified current firmware version
     */
    List<ObuOtaRequest> findByObuFirmwareVersion(String obuFirmwareVersion);

    /**
     * Find OBU OTA requests by requested firmware version
     * 
     * @param requestedFirmwareVersion Requested firmware version
     * @return List of OTA requests requesting the specified firmware version
     */
    List<ObuOtaRequest> findByRequestedFirmwareVersion(String requestedFirmwareVersion);

    /**
     * Find OBU OTA requests by manufacturer
     * 
     * @param manufacturerId Manufacturer ID
     * @return List of OTA requests for the specified manufacturer
     */
    List<ObuOtaRequest> findByManufacturer(Integer manufacturerId);

    /**
     * Find OBU OTA requests with errors
     * 
     * @return List of OTA requests that have errors
     */
    @Query("SELECT o FROM ObuOtaRequest o WHERE o.errorStatus = true")
    List<ObuOtaRequest> findWithErrors();

    /**
     * Find OBU OTA requests without errors
     * 
     * @return List of OTA requests that are successful
     */
    @Query("SELECT o FROM ObuOtaRequest o WHERE o.errorStatus = false")
    List<ObuOtaRequest> findWithoutErrors();

    /**
     * Find OBU OTA requests by manufacturer with errors
     * 
     * @param manufacturerId Manufacturer ID
     * @return List of OTA requests with errors for the specified manufacturer
     */
    @Query("SELECT o FROM ObuOtaRequest o WHERE o.manufacturer = :manufacturerId AND o.errorStatus = true")
    List<ObuOtaRequest> findWithErrorsByManufacturer(@Param("manufacturerId") Integer manufacturerId);

    /**
     * Find OBU OTA requests by manufacturer without errors
     * 
     * @param manufacturerId Manufacturer ID
     * @return List of OTA requests without errors for the specified manufacturer
     */
    @Query("SELECT o FROM ObuOtaRequest o WHERE o.manufacturer = :manufacturerId AND o.errorStatus = false")
    List<ObuOtaRequest> findWithoutErrorsByManufacturer(@Param("manufacturerId") Integer manufacturerId);

    /**
     * Find OBU OTA requests after a specific date
     * 
     * @param requestDatetime Date threshold
     * @return List of OTA requests after the specified date
     */
    List<ObuOtaRequest> findByRequestDatetimeAfter(LocalDateTime requestDatetime);

    /**
     * Find OBU OTA requests before a specific date
     * 
     * @param requestDatetime Date threshold
     * @return List of OTA requests before the specified date
     */
    List<ObuOtaRequest> findByRequestDatetimeBefore(LocalDateTime requestDatetime);

    /**
     * Find OBU OTA requests by origin IP
     * 
     * @param originIp Origin IP address
     * @return List of OTA requests from the specified IP address
     */
    List<ObuOtaRequest> findByOriginIp(String originIp);

    /**
     * Find the latest OTA request for a specific OBU
     * 
     * @param obuSn OBU serial number
     * @return Optional latest OTA request for the specified OBU
     */
    @Query("SELECT o FROM ObuOtaRequest o WHERE o.obuSn = :obuSn ORDER BY o.requestDatetime DESC")
    Optional<ObuOtaRequest> findLatestByObuSn(@Param("obuSn") String obuSn);

    /**
     * Count OTA requests by manufacturer
     * 
     * @param manufacturerId Manufacturer ID
     * @return Count of OTA requests for the specified manufacturer
     */
    long countByManufacturer(Integer manufacturerId);

    /**
     * Count OTA requests with errors
     * 
     * @return Count of OTA requests with errors
     */
    @Query("SELECT COUNT(o) FROM ObuOtaRequest o WHERE o.errorStatus = true")
    long countWithErrors();

    /**
     * Count OTA requests without errors
     * 
     * @return Count of OTA requests without errors
     */
    @Query("SELECT COUNT(o) FROM ObuOtaRequest o WHERE o.errorStatus = false")
    long countWithoutErrors();

    /**
     * Count OTA requests by manufacturer with errors
     * 
     * @param manufacturerId Manufacturer ID
     * @return Count of OTA requests with errors for the specified manufacturer
     */
    @Query("SELECT COUNT(o) FROM ObuOtaRequest o WHERE o.manufacturer = :manufacturerId AND o.errorStatus = true")
    long countWithErrorsByManufacturer(@Param("manufacturerId") Integer manufacturerId);
}
