package us.dot.its.jpo.ode.api.mappers;

import java.util.HashMap;
import java.util.Map;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeCheckResponseDto;
import us.dot.its.jpo.ode.api.models.postgres.dtos.FirmwareUpgradeResultDto;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FirmwareUpgradeMapper {

    /**
     * Convert service response map to FirmwareUpgradeCheckResponseDto
     * The service returns a Map with keys: upgrade_available, upgrade_id,
     * upgrade_name,
     * upgrade_version
     */
    @Named("mapCheckUpgradeResponse")
    default FirmwareUpgradeCheckResponseDto mapCheckUpgradeResponse(Map<String, Object> response) {
        if (response == null) {
            return null;
        }

        FirmwareUpgradeCheckResponseDto dto = new FirmwareUpgradeCheckResponseDto();
        dto.setUpgradeAvailable((Boolean) response.get("upgrade_available"));

        Object upgradeId = response.get("upgrade_id");
        dto.setUpgradeId(upgradeId instanceof Number ? ((Number) upgradeId).longValue() : -1L);

        dto.setUpgradeName((String) response.getOrDefault("upgrade_name", ""));
        dto.setUpgradeVersion((String) response.getOrDefault("upgrade_version", ""));

        return dto;
    }

    /**
     * Convert service response map to Map<String, FirmwareUpgradeResultDto>
     * The service returns a Map where key is RSU IP and value is a Map with code
     * and data
     */
    @Named("mapStartUpgradeResponse")
    default Map<String, FirmwareUpgradeResultDto> mapStartUpgradeResponse(Map<String, Object> response) {
        if (response == null) {
            return new HashMap<>();
        }

        Map<String, FirmwareUpgradeResultDto> resultMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : response.entrySet()) {
            String rsuIp = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap_inner = (Map<String, Object>) value;

                Integer code = null;
                if (resultMap_inner.containsKey("code")) {
                    Object codeObj = resultMap_inner.get("code");
                    code = codeObj instanceof Number ? ((Number) codeObj).intValue() : 500;
                }

                Object data = resultMap_inner.getOrDefault("data", "");

                resultMap.put(rsuIp, new FirmwareUpgradeResultDto(code, data));
            }
        }

        return resultMap;
    }
}
