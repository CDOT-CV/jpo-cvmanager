package us.dot.its.jpo.ode.api.controllers.organizations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import us.dot.its.jpo.ode.api.mappers.OrganizationMapper;
import us.dot.its.jpo.ode.api.mappers.RsuInfoMapper;
import us.dot.its.jpo.ode.api.mappers.UserMapper;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.admin.organization.OrganizationPatch;
import us.dot.its.jpo.ode.api.models.devices.RsuInfoDto;
import us.dot.its.jpo.ode.api.models.organizations.OrganizationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.users.UserDto;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.UserOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;
import us.dot.its.jpo.ode.api.services.OrganizationManagementService;
import us.dot.its.jpo.ode.api.services.PermissionService;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@ApiResponses(value = {
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
})
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    final OrganizationManagementService organizationManagementService;
    final OrganizationMapper organizationMapper;
    final OrganizationRepository organizationRepository;
    final PermissionService permissionService;
    final RsuInfoMapper rsuInfoMapper;
    final RsuRepository rsuRepository;
    final UserMapper userMapper;
    final UserRepository userRepository;
    final RsuOrganizationRepository rsuOrganizationRepository;
    final UserOrganizationRepository userOrganizationRepository;

    @Operation(summary = "Modify Organization", description = "Updates an organization's name, email, and user/RSU/intersection memberships. Optionally bulk-applies tim_deposit and snmp_monitoring to all RSUs in the org.")
    @RequestMapping(path = "", method = RequestMethod.PATCH, produces = "application/json", consumes = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#patch.origName, 'ADMIN')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid input"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role in the target organization"),
            @ApiResponse(responseCode = "404", description = "Not Found - Organization, user, RSU, or intersection not found"),
    })
    public ResponseEntity<OrganizationDto> modifyOrganization(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Organization patch payload", required = true) @RequestBody @Valid OrganizationPatch patch) {
        OrganizationDto result = organizationManagementService.modifyOrganization(
                patch, permissionService.getCvManagerAuthToken());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get Organizations", description = "Retrieves all organizations where the authenticated user has ADMIN role. Superusers receive all organizations.")
    @RequestMapping(path = "", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role"),
    })
    public List<OrganizationDto> getOrganizations() {
        if (permissionService.isSuperUser()) {
            return organizationRepository.findAll().stream()
                    .map(organizationMapper::toDto)
                    .collect(Collectors.toList());
        }
        List<Organization> qualifiedOrgs = permissionService.getCvManagerAuthToken()
                .getQualifiedOrgList(UserRole.ADMIN);
        return organizationRepository
                .findByIdIn(qualifiedOrgs.stream().map(Organization::getId).collect(Collectors.toList())).stream()
                .map(organizationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Get RSU IPs by Organization", description = "Retrieves a list of IP addresses for all RSUs belonging to the specified organization.")
    @RequestMapping(path = "rsus", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role"),
    })
    public List<String> getRsuIpsByOrganization(
            @RequestHeader(name = "Organization", required = true) String organization) {
        return rsuOrganizationRepository.findAllRsuIpsByOrganizationName(organization).stream()
                .map(InetAddress::getHostAddress)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Get RSU Organization Assignments", description = "Retrieves a list of organization names that the specified RSU is assigned to.")
    @RequestMapping(path = "rsus/{rsuIp}", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasRsu(#rsuIp, 'ADMIN') and @PermissionService.hasRole('ADMIN'))")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid RSU IP address format"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role with access to the RSU requested"),
    })
    public List<String> getRsuOrganizationAssignments(
            @Parameter(description = "RSU IP address", example = "192.168.1.1", required = true) @PathVariable(name = "rsuIp") String rsuIp) {
        try {
            return rsuRepository.findAllOrganizationNamesByIpv4Address(InetAddress.getByName(rsuIp));
        } catch (UnknownHostException e) {
            log.error("Invalid RSU IP address: {}", rsuIp, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid RSU IP address: " + rsuIp, e);
        }
    }

    @Operation(summary = "Get RSU IPs not in Organization", description = "Retrieves a list of IP addresses for all RSUs not belonging to the specified organization.")
    @RequestMapping(path = "rsus/available", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role"),
    })
    public List<RsuInfoDto> getRsuIpsNotInOrganization(
            @RequestHeader(name = "Organization", required = true) String organization) {
        return rsuOrganizationRepository.findAllRsusNotInOrganizationName(organization).stream()
                .map(rsuInfoMapper::toDto)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Get User Emails by Organization", description = "Retrieves a list of user emails for all users belonging to the specified organization.")
    @RequestMapping(path = "users", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role"),
    })
    public List<String> getUserEmailsByOrganization(
            @RequestHeader(name = "Organization", required = true) String organization) {
        return userOrganizationRepository.findAllUserEmailsByOrganizationName(organization);
    }

    @Operation(summary = "Get User Organization Assignments", description = "Retrieves a list of organization names that the specified user is assigned to.")
    @RequestMapping(path = "users/{email}", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || (@PermissionService.hasUser(#email, 'ADMIN') and @PermissionService.hasRole('ADMIN'))")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role with access to the user requested"),
    })
    public List<String> getUserOrganizationAssignments(
            @Parameter(description = "User email address", example = "user@example.com", required = true) @PathVariable(name = "email") String email) {
        return userRepository.findAllOrganizationNamesByEmail(email);
    }

    @Operation(summary = "Get Users Not In Organization", description = "Retrieves a list of user emails for all users not belonging to the specified organization.")
    @RequestMapping(path = "users/available", method = RequestMethod.GET, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRole('ADMIN')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role"),
    })
    public List<UserDto> getUserEmailsNotInOrganization(
            @RequestHeader(name = "Organization", required = true) String organization) {
        return userOrganizationRepository.findAllUserEmailsNotInOrganizationName(organization).stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Operation(summary = "Delete Organization", description = "Deletes an organization and all its junction-table relationships. Refuses deletion if any RSU, intersection, or user would become orphaned.")
    @RequestMapping(path = "/{orgName}", method = RequestMethod.DELETE, produces = "application/json")
    @PreAuthorize("@PermissionService.isSuperUser() || @PermissionService.hasRoleInOrg(#orgName, 'ADMIN')")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Success"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires SUPER_USER or ADMIN role in the target organization"),
            @ApiResponse(responseCode = "404", description = "Not Found - Organization not found"),
            @ApiResponse(responseCode = "409", description = "Conflict - Organization has RSUs, intersections, or users that would become orphaned"),
    })
    public ResponseEntity<Void> deleteOrganization(
            @Parameter(description = "Organization name", example = "TestOrg", required = true) @PathVariable(name = "orgName") String orgName) {
        organizationManagementService.deleteOrganization(orgName);
        return ResponseEntity.noContent().build();
    }
}