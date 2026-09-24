package us.dot.its.jpo.ode.api.controllers.admin;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import us.dot.its.jpo.ode.api.models.storage.FirmwareRuleModels.*;
import us.dot.its.jpo.ode.api.services.FirmwareRuleService;

@RestController
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(name = "enable.api", havingValue = "true")
@RequestMapping(value = "/admin/firmware", produces = "application/json")
@PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
@Tag(name = "Admin Firmware", description = "Manage firmware upgrade paths")
public class AdminFirmwareRuleController {
    private final FirmwareRuleService rules;

    @GetMapping("/upgrade-rules")
    @Operation(summary = "List firmware upgrade paths, including legacy paths")
    public List<Rule> list() { return rules.list(); }

    @GetMapping("/images/{imageId}/upgrade-rules")
    @Operation(summary = "Get eligible source versions and existing paths for a firmware image")
    public Options options(@PathVariable @Positive Integer imageId) { return rules.options(imageId); }

    @PutMapping(value = "/images/{imageId}/upgrade-rules", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Assign selected source versions to a verified destination atomically")
    public void assign(@PathVariable @Positive Integer imageId, @Valid @RequestBody Assignments request) {
        rules.assign(imageId, request);
    }

    @DeleteMapping("/upgrade-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an upgrade path without deleting either firmware image")
    public void delete(@PathVariable @Positive Integer ruleId,
            @RequestParam(name = "expected_target_id") @Positive Integer expectedTargetId) {
        rules.delete(ruleId, expectedTargetId);
    }
}
