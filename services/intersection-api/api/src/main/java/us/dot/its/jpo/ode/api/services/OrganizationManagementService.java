package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import us.dot.its.jpo.ode.api.mappers.OrganizationMapper;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.admin.organization.OrganizationPatch;
import us.dot.its.jpo.ode.api.models.admin.organization.UserRoleAssignment;
import us.dot.its.jpo.ode.api.models.keycloak.CvManagerAuthToken;
import us.dot.its.jpo.ode.api.models.organizations.OrganizationDto;
import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.IntersectionOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Role;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOption;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.User;
import us.dot.its.jpo.ode.api.models.postgres.tables.UserOrganization;
import us.dot.its.jpo.ode.api.repositories.IntersectionOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RoleRepository;
import us.dot.its.jpo.ode.api.repositories.RsuOptionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;
import us.dot.its.jpo.ode.api.repositories.UserOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationManagementService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final RoleRepository roleRepository;
    private final RsuRepository rsuRepository;
    private final RsuOrganizationRepository rsuOrganizationRepository;
    private final RsuOptionRepository rsuOptionRepository;
    private final IntersectionRepository intersectionRepository;
    private final IntersectionOrganizationRepository intersectionOrganizationRepository;
    private final OrganizationMapper organizationMapper;

    /**
     * Applies all modifications described in the patch to the specified
     * organization.
     * Authorization must be enforced by the caller before invoking this method.
     *
     * <p>
     * Steps performed in a single transaction:
     * <ol>
     * <li>Verify the caller has ADMIN rights over the target organization.</li>
     * <li>Update the organization's name and email.</li>
     * <li>Bulk-apply RSU option flags when present in the patch.</li>
     * <li>Add / modify / remove user memberships.</li>
     * <li>Add / remove RSU associations.</li>
     * <li>Add / remove intersection associations.</li>
     * </ol>
     *
     * @param patch     the patch request body
     * @param authToken the authenticated user's token
     * @return the updated organization as a DTO
     */
    @Transactional
    public OrganizationDto modifyOrganization(OrganizationPatch patch, CvManagerAuthToken authToken) {
        List<Organization> authorizedOrgs = authToken.getQualifiedOrgList(UserRole.ADMIN);

        // Step 1: Authorization guard — the caller must be ADMIN in the target org
        if (!authToken.isSuperUser()
                && authorizedOrgs.stream().noneMatch(org -> org.getId().equals(patch.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "User does not have ADMIN permission over organization: " + patch.getId());
        }

        // Step 2: Load and update the organization record
        Organization org = organizationRepository.findById(patch.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Organization not found: " + patch.getId()));

        if (patch.getName() != null) {
            org.setName(patch.getName());
        }
        if (patch.getEmail() != null) {
            org.setEmail(patch.getEmail());
        }
        Organization savedOrg = organizationRepository.save(org);
        log.debug("Organization '{}' updated", patch.getId());

        // Step 3: Bulk-apply RSU option flags (tim_deposit / snmp_monitoring)
        if (patch.getTimDeposit() != null || patch.getSnmpMonitoring() != null) {
            applyBulkRsuOptions(org, patch.getTimDeposit(), patch.getSnmpMonitoring());
        }

        // Step 4: Add users
        handleUsersToAdd(patch.getUsersToAdd(), org);

        // Step 5: Modify user roles
        handleUsersToModify(patch.getUsersToModify(), org);

        // Step 6: Remove users
        if (patch.getUsersToRemove() != null && !patch.getUsersToRemove().isEmpty()) {
            userOrganizationRepository.deleteByUserEmailsAndOrganization(
                    patch.getUsersToRemove(), org);
            log.debug("Removed {} user(s) from org '{}'", patch.getUsersToRemove().size(), org.getName());
        }

        // Step 7: Add RSU associations
        handleRsusToAdd(patch.getRsusToAdd(), org);

        // Step 8: Remove RSU associations
        if (patch.getRsusToRemove() != null && !patch.getRsusToRemove().isEmpty()) {
            List<InetAddress> addresses = resolveIpAddresses(patch.getRsusToRemove());
            rsuOrganizationRepository.deleteByRsuIpv4AddressesAndOrganization(addresses, org);
            log.debug("Removed {} RSU(s) from org '{}'", addresses.size(), org.getName());
        }

        // Step 9: Add intersection associations
        handleIntersectionsToAdd(patch.getIntersectionsToAdd(), org);

        // Step 10: Remove intersection associations
        if (patch.getIntersectionsToRemove() != null && !patch.getIntersectionsToRemove().isEmpty()) {
            List<String> numberStrings = patch.getIntersectionsToRemove().stream()
                    .map(Object::toString)
                    .toList();
            intersectionOrganizationRepository.deleteByIntersectionNumbersAndOrganization(
                    numberStrings, org);
            log.debug("Removed {} intersection(s) from org '{}'", numberStrings.size(), org.getName());
        }

        return organizationMapper.toDto(savedOrg);
    }

    /**
     * Deletes an organization and all its junction-table relationships.
     * Refuses deletion if any RSU, intersection, or user would become orphaned
     * (i.e., associated with no organization after the delete).
     *
     * @param org the organization entity to delete
     * @throws ResponseStatusException            with 404 if the organization does
     *                                            not exist
     * @throws OrganizationHasDependentsException with 409 if orphaned RSUs,
     *                                            intersections, or users would
     *                                            result
     */
    @Transactional
    public void deleteOrganization(Organization org) {

        if (rsuOrganizationRepository.existsOrphanRsuInOrganization(org)) {
            throw new OrganizationHasDependentsException(
                    "Cannot delete organization that has one or more RSUs only associated with this organization");
        }

        if (intersectionOrganizationRepository.existsOrphanIntersectionInOrganization(org)) {
            throw new OrganizationHasDependentsException(
                    "Cannot delete organization that has one or more Intersections only associated with this organization");
        }

        if (userOrganizationRepository.existsOrphanUserInOrganization(org)) {
            throw new OrganizationHasDependentsException(
                    "Cannot delete organization that has one or more users only associated with this organization");
        }

        userOrganizationRepository.deleteAllByOrganization(org);
        rsuOrganizationRepository.deleteAllByOrganization(org);
        intersectionOrganizationRepository.deleteAllByOrganization(org);
        organizationRepository.delete(org);
        log.debug("Organization '{}' deleted", org.getName());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void applyBulkRsuOptions(Organization organization, Boolean timDeposit, Boolean snmpMonitoring) {
        List<InetAddress> rsuIps = rsuOrganizationRepository.findAllRsuIpsByOrganizationId(organization.getId());
        if (rsuIps.isEmpty()) {
            return;
        }

        List<Rsu> rsus = rsuRepository.findByIpv4AddressIn(rsuIps);
        List<Integer> rsuIds = rsus.stream().map(Rsu::getId).toList();

        // Batch-load all existing RsuOption records and key them by RSU id
        Map<Integer, RsuOption> existingOptions = rsuOptionRepository.findAllById(rsuIds).stream()
                .collect(Collectors.toMap(RsuOption::getId, o -> o));

        List<RsuOption> optionsToSave = new ArrayList<>();
        for (Rsu rsu : rsus) {
            RsuOption option = existingOptions.getOrDefault(rsu.getId(), null);
            Boolean modified = false;
            if (option == null) {
                option = new RsuOption();
                option.setRsu(rsu);
                modified = true;
            }
            if (timDeposit != null) {
                option.setTimDeposit(timDeposit);
                modified = true;
            }
            if (snmpMonitoring != null) {
                option.setSnmpMonitoring(snmpMonitoring);
                modified = true;
            }
            // Only save if there were changes
            if (modified) {
                optionsToSave.add(option);
            }
        }
        rsuOptionRepository.saveAll(optionsToSave);
        log.debug("Bulk-applied RSU options to {} RSU(s) in org '{}'", optionsToSave.size(), organization.getName());
    }

    private void handleUsersToAdd(List<UserRoleAssignment> assignments, Organization organization) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }

        // Cache role lookups to avoid redundant DB hits across assignments
        Map<String, Role> roleCache = new HashMap<>();
        List<UserOrganization> toSave = new ArrayList<>();

        for (UserRoleAssignment assignment : assignments) {
            // Check membership first — skip user/role loading if already a member
            boolean alreadyMember = userOrganizationRepository
                    .findByUser_EmailAndOrganization(assignment.getEmail(), organization)
                    .isPresent();
            if (alreadyMember) {
                log.debug("User '{}' is already a member of org '{}', skipping add", assignment.getEmail(),
                        organization.getName());
                continue;
            }

            User user = userRepository.findByEmail(assignment.getEmail())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found with email: " + assignment.getEmail()));

            Role role = roleCache.computeIfAbsent(assignment.getRole(),
                    r -> roleRepository.findByName(r)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "Role not found: " + r)));

            UserOrganization userOrg = new UserOrganization();
            userOrg.setUser(user);
            userOrg.setRole(role);
            userOrg.setOrganization(organization);
            toSave.add(userOrg);
            log.debug("Queued user '{}' with role '{}' for addition to org '{}'", assignment.getEmail(),
                    assignment.getRole(), organization.getName());
        }
        userOrganizationRepository.saveAll(toSave);
    }

    private void handleUsersToModify(List<UserRoleAssignment> assignments, Organization organization) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }

        // Pre-cache all unique roles referenced in this batch to avoid redundant DB
        // hits
        Map<String, Role> roleCache = new HashMap<>();
        List<UserOrganization> toSave = new ArrayList<>();

        for (UserRoleAssignment assignment : assignments) {
            UserOrganization userOrg = userOrganizationRepository
                    .findByUser_EmailAndOrganization(assignment.getEmail(), organization)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User '" + assignment.getEmail() + "' is not a member of organization: "
                                    + organization.getName()));

            Role role = roleCache.computeIfAbsent(assignment.getRole(),
                    r -> roleRepository.findByName(r)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "Role not found: " + r)));

            userOrg.setRole(role);
            toSave.add(userOrg);
            log.debug("Queued user '{}' for role update to '{}' in org '{}'", assignment.getEmail(),
                    assignment.getRole(), organization.getName());
        }
        userOrganizationRepository.saveAll(toSave);
    }

    private void handleRsusToAdd(List<String> ipStrings, Organization organization) {
        if (ipStrings == null || ipStrings.isEmpty()) {
            return;
        }

        List<RsuOrganization> toSave = new ArrayList<>();
        for (String ipString : ipStrings) {
            InetAddress ip = resolveIpAddress(ipString);

            // Check assignment first — skip loading the RSU entity if already assigned
            boolean alreadyAssigned = rsuOrganizationRepository
                    .findByRsuIpv4AddressAndOrganization(ip, organization)
                    .isPresent();
            if (alreadyAssigned) {
                log.debug("RSU '{}' is already assigned to org '{}', skipping add", ipString, organization.getName());
                continue;
            }

            Rsu rsu = rsuRepository.findByIpv4Address(ip);
            if (rsu == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "RSU not found with IP: " + ipString);
            }

            RsuOrganization rsuOrg = new RsuOrganization();
            rsuOrg.setRsu(rsu);
            rsuOrg.setOrganization(organization);
            toSave.add(rsuOrg);
            log.debug("Queued RSU '{}' for addition to org '{}'", ipString, organization.getName());
        }
        rsuOrganizationRepository.saveAll(toSave);
    }

    private void handleIntersectionsToAdd(List<Integer> intersectionIds, Organization organization) {
        if (intersectionIds == null || intersectionIds.isEmpty()) {
            return;
        }

        List<IntersectionOrganization> toSave = new ArrayList<>();
        for (Integer intersectionId : intersectionIds) {
            String idString = intersectionId.toString();

            // Check assignment first — skip loading the intersection entity if already
            // assigned
            boolean alreadyAssigned = intersectionOrganizationRepository
                    .findByIntersection_IntersectionNumberAndOrganization(idString, organization)
                    .isPresent();
            if (alreadyAssigned) {
                log.debug("Intersection '{}' is already assigned to org '{}', skipping add", intersectionId,
                        organization.getName());
                continue;
            }

            Intersection intersection = intersectionRepository.findByIntersectionNumber(idString)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Intersection not found with ID: " + intersectionId));

            IntersectionOrganization io = new IntersectionOrganization();
            io.setIntersection(intersection);
            io.setOrganization(organization);
            toSave.add(io);
            log.debug("Queued intersection '{}' for addition to org '{}'", intersectionId, organization.getName());
        }
        intersectionOrganizationRepository.saveAll(toSave);
    }

    private List<InetAddress> resolveIpAddresses(List<String> ipStrings) {
        return ipStrings.stream()
                .map(this::resolveIpAddress)
                .toList();
    }

    public static class OrganizationHasDependentsException extends RuntimeException {
        public OrganizationHasDependentsException(String message) {
            super(message);
        }
    }

    private InetAddress resolveIpAddress(String ipString) {
        try {
            return InetAddress.getByName(ipString);
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid IP address: " + ipString, e);
        }
    }
}
