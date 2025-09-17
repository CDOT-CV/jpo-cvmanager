package us.dot.its.jpo.ode.api.controllers.firmware;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import us.dot.its.jpo.ode.api.accessors.firmware.RsuFirmwareRepository;
import us.dot.its.jpo.ode.api.accessors.firmware.RsuFirmwareStatusRepository;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareFile;
import us.dot.its.jpo.ode.api.models.firmware.FirmwareRule;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsus;
import us.dot.its.jpo.ode.api.services.firmware.FirmwareServiceException;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for RSU firmware management
 * Provides endpoints for CRUD operations on RSU firmware files and rules
 */
@Slf4j
@RestController
@RequestMapping("/admin-firmware/rsu")
@CrossOrigin(origins = "*", maxAge = 3600)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
public class RsuFirmwareController {

    @Autowired
    private RsuFirmwareRepository rsuFirmwareRepository;

    @Autowired
    private RsuFirmwareStatusRepository rsuFirmwareStatusRepository;

    /**
     * Get all RSU firmware files with their rules
     * 
     * @return List of RSU firmware files
     */
    @Operation(summary = "Get RSU Firmware Files", description = "Returns a list of all RSU firmware files with their associated rules")
    @GetMapping("/files")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<Map<String, Object>> getFirmwareFiles() {
        try {
            List<FirmwareFile> firmwareFiles = rsuFirmwareRepository.getFirmwareFiles();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RSU firmware files retrieved successfully");
            response.put("firmware_files", firmwareFiles);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to retrieve RSU firmware files", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to retrieve RSU firmware files: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get RSU firmware file by ID
     * 
     * @param firmwareId ID of the firmware file
     * @return Firmware file details
     */
    @GetMapping("/files/{firmwareId}")
    public ResponseEntity<Map<String, Object>> getFirmwareFile(@PathVariable Integer firmwareId) {
        try {
            FirmwareFile firmwareFile = rsuFirmwareRepository.getFirmwareFile(firmwareId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RSU firmware file retrieved successfully");
            response.put("firmware_file", firmwareFile);

            return ResponseEntity.ok(response);
        } catch (FirmwareServiceException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to retrieve RSU firmware file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Add new RSU firmware version with upgrade rules
     * 
     * @param file        Firmware file to upload
     * @param description Optional description
     * @param rules       JSON string of upgrade rules
     * @param createdBy   User who created the firmware
     * @return Created firmware file
     */
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> addFirmwareVersion(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("rules") String rules,
            @RequestParam("created_by") String createdBy) {

        try {
            // Parse rules JSON (simplified - in real implementation, use proper JSON
            // parsing)
            List<FirmwareRule> firmwareRules = parseRulesFromJson(rules);

            FirmwareFile firmwareFile = rsuFirmwareRepository.addFirmwareVersion(
                    file, description, firmwareRules, createdBy);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RSU firmware version added successfully");
            response.put("firmware_file", firmwareFile);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (FirmwareServiceException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to add RSU firmware version: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Modify RSU firmware version rules
     * 
     * @param firmwareId ID of the firmware file
     * @param rules      Updated list of rules
     * @param modifiedBy User who modified the rules
     * @return Updated firmware file
     */
    @PutMapping("/files/{firmwareId}/rules")
    public ResponseEntity<Map<String, Object>> modifyFirmwareVersion(
            @PathVariable Integer firmwareId,
            @RequestBody List<FirmwareRule> rules,
            @RequestParam("modified_by") String modifiedBy) {

        try {
            FirmwareFile firmwareFile = rsuFirmwareRepository.modifyFirmwareVersion(
                    firmwareId, rules, modifiedBy);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RSU firmware version rules updated successfully");
            response.put("firmware_file", firmwareFile);

            return ResponseEntity.ok(response);
        } catch (FirmwareServiceException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to modify RSU firmware version: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Remove RSU firmware version
     * 
     * @param firmwareId ID of the firmware file to remove
     * @param removedBy  User who removed the firmware
     * @return Success response
     */
    @DeleteMapping("/files/{firmwareId}")
    public ResponseEntity<Map<String, Object>> removeFirmwareVersion(
            @PathVariable Integer firmwareId,
            @RequestParam("removed_by") String removedBy) {

        try {
            rsuFirmwareRepository.removeFirmwareVersion(firmwareId, removedBy);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RSU firmware version removed successfully");

            return ResponseEntity.ok(response);
        } catch (FirmwareServiceException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to remove RSU firmware version: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Download RSU firmware file
     * 
     * @param firmwareId ID of the firmware file
     * @return File download response
     */
    @GetMapping("/files/{firmwareId}/download")
    public ResponseEntity<byte[]> downloadFirmwareFile(@PathVariable Integer firmwareId) {
        try {
            InputStream fileStream = rsuFirmwareRepository.downloadFirmwareFile(firmwareId);
            FirmwareFile firmwareFile = rsuFirmwareRepository.getFirmwareFile(firmwareId);

            byte[] fileBytes = fileStream.readAllBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", firmwareFile.getName());
            headers.setContentLength(fileBytes.length);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get RSU firmware upgrade statuses
     * 
     * @return List of RSU firmware statuses
     */
    @GetMapping("/statuses")
    public ResponseEntity<Map<String, Object>> getFirmwareStatuses() {
        try {
            List<Rsus> statuses = rsuFirmwareStatusRepository.findAll();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RSU firmware statuses retrieved successfully");
            response.put("firmware_statuses", statuses);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to retrieve RSU firmware statuses: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Parse rules from JSON string (simplified implementation)
     * In a real implementation, use proper JSON parsing with Jackson or Gson
     */
    private List<FirmwareRule> parseRulesFromJson(String rulesJson) throws FirmwareServiceException {
        // This is a simplified implementation
        // In production, use proper JSON parsing
        if (rulesJson == null || rulesJson.trim().isEmpty()) {
            throw new FirmwareServiceException("Rules JSON is required");
        }

        // For now, return empty list - implement proper JSON parsing
        return List.of();
    }
}
