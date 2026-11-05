package us.dot.its.jpo.ode.api.services;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.mappers.INetMapper;
import us.dot.its.jpo.ode.api.mappers.IntersectionMapper;
import us.dot.its.jpo.ode.api.models.UserRole;
import us.dot.its.jpo.ode.api.models.admin.intersection.AllowedSelections;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionCreate;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionDto;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionListResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionPatch;
import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.IntersectionOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuIntersection;
import us.dot.its.jpo.ode.api.repositories.IntersectionOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuIntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

/**
 * Service for admin intersection management.
 *
 * This service is responsible only for business logic and repository
 * operations. All authorization
 * (role checks, intersection resource access, and org restriction enforcement)
 * is handled by AdminIntersectionController before this service is called.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminIntersectionService {

    private final IntersectionRepository intersectionRepository;
    private final IntersectionOrganizationRepository intersectionOrganizationRepository;
    private final RsuIntersectionRepository rsuIntersectionRepository;
    private final RsuRepository rsuRepository;
    private final IntersectionMapper intersectionMapper;
    private final INetMapper inetMapper;
    private final PermissionService permissionService;

    /**
     * Returns a single intersection by intersection_number
     * Access is already verified by @PreAuthorize in the controller; all
     * intersection orgs are returned.
     *
     * @param intersectionId the intersection_number to look up
     * @return response containing intersection_data and allowed_selections
     */
    public IntersectionDto getIntersection(Integer intersectionId) {
        Intersection intersection = intersectionRepository.findByIntersectionNumberWithOrgs(intersectionId)
                .orElseThrow(() -> {
                    log.error("Intersection with id {} not found", intersectionId);
                    return new EntityNotFoundException("Intersection with id " + intersectionId + " not found");
                });

        IntersectionDto dto = intersectionMapper.toDto(intersection);

        List<Integer> orgIds = intersection.getIntersectionOrganizations().stream()
                .filter(io -> io.getOrganization() != null)
                .map(io -> io.getOrganization().getId())
                .collect(Collectors.toList());
        dto.setOrganizations(orgIds);

        List<String> rsuIps = rsuIntersectionRepository.findRsuIpsByIntersectionNumber(intersectionId)
                .stream()
                .map(inetMapper::mapInetAddressToString)
                .collect(Collectors.toList());
        dto.setRsus(rsuIps);

        log.debug("Successfully fetched intersection {}. Org count: {}, RSU count: {}", intersectionId, orgIds.size(),
                        rsuIps.size());
        return dto;
    }

    /**
     * Returns the organizations and RSU IPs the current user may assign to an
     * intersection.
     * Superusers receive all orgs and RSUs; non-superusers receive only those
     * within their
     * OPERATOR-qualified organizations.
     *
     * @return allowed organizations and RSU IP addresses
     */
    public AllowedSelections getAllowedSelections(List<Organization> qualifiedOrgs) {
        List<Integer> operatorOrgIds = qualifiedOrgs.stream()
                .map(Organization::getId)
                .collect(Collectors.toList());

        List<InetAddress> qualifiedRsuIps;
        if (permissionService.isSuperUser()) {
            qualifiedRsuIps = rsuRepository.findAll().stream()
                    .map(rsu -> rsu.getIpv4Address())
                    .collect(Collectors.toList());
        } else {
            qualifiedRsuIps = rsuRepository.findAllowedRsuIpsInOrganizations(permissionService.getCvManagerAuthToken()
                    .getQualifiedOrgList(UserRole.OPERATOR));
        }

        List<String> rsuIps = qualifiedRsuIps.stream()
                .map(inetMapper::mapInetAddressToString)
                .collect(Collectors.toList());

        return new AllowedSelections(operatorOrgIds, rsuIps);
    }

    /**
     * Creates a new intersection with organization and RSU associations.
     * The controller has already enforced all authorization before this is called.
     * All writes are wrapped in a single transaction; a failure at any step rolls
     * back entirely.
     * Validates that all referenced organizations and RSUs exist before writing.
     *
     * @param create the create request body
     */
    @Transactional
    public void createIntersection(IntersectionCreate create, List<Organization> qualifiedOrgs) {
        // Step 1: Build and save the Intersection entity
        Intersection intersection = intersectionMapper.toEntity(create);
        intersection = intersectionRepository.save(intersection);
        log.debug("Intersection saved with PK: {}", intersection.getId());

        // Step 2: Create organization associations
        List<Organization> orgsToCreate = qualifiedOrgs.stream()
                .filter(org -> create.getOrganizations().contains(org.getId()))
                .collect(Collectors.toList());

        if (orgsToCreate.size() != create.getOrganizations().size()) {
            List<Integer> missingOrgIds = create.getOrganizations().stream()
                    .filter(orgId -> orgsToCreate.stream().noneMatch(org -> org.getId().equals(orgId)))
                    .collect(Collectors.toList());
            throw new EntityNotFoundException("Organization(s) not found: " + missingOrgIds);
        }
        Intersection savedIntersection = intersection;
        saveOrgAssociations(orgsToCreate, savedIntersection);

        // Step 3: Create RSU associations (skip if empty)
        if (!create.getRsus().isEmpty()) {
            List<InetAddress> inetAddresses = create.getRsus().stream()
                    .map(inetMapper::mapStringToInetAddress)
                    .collect(Collectors.toList());
            List<Rsu> rsus = rsuRepository.findByIpv4AddressIn(inetAddresses);
            if (rsus.size() != create.getRsus().size()) {
                List<String> foundIps = rsus.stream()
                        .map(rsu -> inetMapper.mapInetAddressToString(rsu.getIpv4Address()))
                        .toList();
                List<String> missing = create.getRsus().stream()
                        .filter(ip -> !foundIps.contains(ip))
                        .toList();
                throw new EntityNotFoundException("RSU(s) not found: " + missing);
            }
            List<RsuIntersection> rsuAssocs = rsus.stream()
                    .map(rsu -> {
                        RsuIntersection ri = new RsuIntersection();
                        ri.setIntersection(savedIntersection);
                        ri.setRsu(rsu);
                        return ri;
                    })
                    .collect(Collectors.toList());
            rsuIntersectionRepository.saveAll(rsuAssocs);
            log.debug("Step 3: Saved {} RSU association(s).", rsuAssocs.size());
        } else {
            log.debug("Step 3: No RSU associations to create.");
        }

        log.info("Successfully created intersection {}", create.getIntersectionId());
    }

    /**
     * Saves the associations between a given list of organizations and an
     * intersection.
     *
     * @param orgs              the list of organizations to be associated with the
     *                          intersection
     * @param savedIntersection the intersection with which the organizations will
     *                          be associated
     */
    private void saveOrgAssociations(List<Organization> orgs, Intersection savedIntersection) {
        List<IntersectionOrganization> orgAssocs = orgs.stream()
                .map(org -> {
                    IntersectionOrganization io = new IntersectionOrganization();
                    io.setIntersection(savedIntersection);
                    io.setOrganization(org);
                    return io;
                })
                .collect(Collectors.toList());
        intersectionOrganizationRepository.saveAll(orgAssocs);
    }

    /**
     * Returns all intersections for the specified organization.
     * The organization parameter is always required; the controller enforces this
     * via a
     * mandatory request header.
     *
     * @param organization the organization to scope results to
     * @return response containing intersection_data as a list of intersections for
     *         the organization
     */
    public IntersectionListResponse getAllIntersections(Organization organization) {
        List<Intersection> intersections = intersectionRepository.findAllByOrganization(organization);

        if (intersections.isEmpty()) {
            log.info("No intersections found for organization '{}'", organization);
            return new IntersectionListResponse(Collections.emptyList());
        }

        return getIntersectionListResponse(intersections);
    }

    /**
     * Returns all intersections not associated with the specified organization.
     * Used to populate the "available to add" dropdown when adding intersections to
     * an organization. The controller enforces authorization (ADMIN role or
     * superuser) before this is called.
     *
     * @param organization the organization to exclude intersections from
     * @return response containing intersection_data as a list of intersections
     *         currently not assigned to the organization
     */
    public IntersectionListResponse getIntersectionsNotInOrganization(Organization organization) {
        List<Intersection> intersections = intersectionOrganizationRepository
                .findAllIntersectionsNotInOrganization(organization);

        if (intersections.isEmpty()) {
            log.info("No intersections found outside organization '{}'", organization);
            return new IntersectionListResponse(Collections.emptyList());
        }

        return getIntersectionListResponse(intersections);
    }

    private IntersectionListResponse getIntersectionListResponse(List<Intersection> intersections) {
        List<IntersectionDto> dtos = intersections.stream()
                .map(intersectionMapper::toDto)
                .collect(Collectors.toList());

        List<String> intersectionNumbers = intersections.stream()
                .map(Intersection::getIntersectionNumber)
                .collect(Collectors.toList());

        Map<Integer, List<String>> rsusByIntersection = loadRsuIpsByIntersection(intersectionNumbers);
        log.debug("RSU IP mapping resolved for {}/{} intersections.", rsusByIntersection.size(),
                intersectionNumbers.size());

        for (IntersectionDto dto : dtos) {
            dto.setRsus(rsusByIntersection.getOrDefault(dto.getIntersectionId(), Collections.emptyList()));
        }

        log.debug("Successfully fetched {} intersections.", dtos.size());
        return new IntersectionListResponse(dtos);
    }

    /**
     * Updates an intersection's properties and modifies its org/RSU relationships.
     * The controller has already enforced all authorization before this is called.
     * Wraps all writes in a single transaction.
     *
     * @param patch the patch request body
     */
    @Transactional
    public void patchIntersection(IntersectionPatch patch, List<Organization> qualifiedOrgs) {
        String origNumber = patch.getOrigIntersectionId().toString();

        Intersection intersection = intersectionRepository.findByIntersectionNumber(origNumber)
                .orElseThrow(() -> {
                    log.error("Intersection not found for patching: {}", origNumber);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Intersection not found: " + origNumber);
                });

        var updatedIntersection = intersectionMapper.partialUpdate(intersection, patch);

        intersectionRepository.save(updatedIntersection);

        // Handle organizations to add
        if (patch.getOrganizationsToAdd() != null && !patch.getOrganizationsToAdd().isEmpty()) {
            List<Organization> orgsToAdd = qualifiedOrgs.stream()
                    .filter(org -> patch.getOrganizationsToAdd().contains(org.getId()))
                    .toList();
            if (orgsToAdd.size() != patch.getOrganizationsToAdd().size()) {
                log.warn("Intersection Patch Requested {} org(s) to add but only {} resolved in DB. Requested: {}",
                        patch.getOrganizationsToAdd().size(), orgsToAdd.size(), patch.getOrganizationsToAdd());
            }
            saveOrgAssociations(orgsToAdd, intersection);
        }

        // Handle organizations to remove
        if (patch.getOrganizationsToRemove() != null && !patch.getOrganizationsToRemove().isEmpty()) {
            List<Integer> orgsToRemove = qualifiedOrgs.stream()
                    .filter(org -> patch.getOrganizationsToRemove().contains(org.getId()))
                    .map(Organization::getId)
                    .toList();
            intersectionOrganizationRepository.deleteByIntersectionNumberAndOrganizationIdsIn(
                    updatedIntersection.getIntersectionNumber(), orgsToRemove);
        }

        // Handle RSUs to add
        if (!patch.getRsusToAdd().isEmpty()) {
            List<InetAddress> ipsToAdd = patch.getRsusToAdd().stream()
                    .map(inetMapper::mapStringToInetAddress)
                    .collect(Collectors.toList());
            List<Rsu> rsus = rsuRepository.findByIpv4AddressIn(ipsToAdd);
            if (rsus.size() != patch.getRsusToAdd().size()) {
                log.warn("Intersection Patch Requested {} RSU(s) to add but only {} resolved in DB. Requested: {}",
                        patch.getRsusToAdd().size(), rsus.size(), patch.getRsusToAdd());
            }
            List<RsuIntersection> newRsuAssocs = rsus.stream()
                    .filter(rsu -> !rsuIntersectionRepository.existsByRsuAndIntersection(rsu, intersection))
                    .map(rsu -> {
                        RsuIntersection ri = new RsuIntersection();
                        ri.setIntersection(intersection);
                        ri.setRsu(rsu);
                        return ri;
                    })
                    .collect(Collectors.toList());
            rsuIntersectionRepository.saveAll(newRsuAssocs);
        }

        // Step 5: Remove RSU associations
        if (!patch.getRsusToRemove().isEmpty()) {
            List<InetAddress> ipsToRemove = patch.getRsusToRemove().stream()
                    .map(inetMapper::mapStringToInetAddress)
                    .collect(Collectors.toList());
            rsuIntersectionRepository.deleteByIntersectionNumberAndRsuIpv4AddressIn(
                    updatedIntersection.getIntersectionNumber(), ipsToRemove);
        }
    }

    /**
     * Deletes an intersection and all its relationship records.
     * The controller has already enforced all authorization before this is called.
     * Wraps all writes in a single transaction (fixes known issue #1).
     * Throws 404 if the intersection does not exist (fixes known issue #2).
     *
     * @param intersectionId the intersection_number to delete
     */
    @Transactional
    public void deleteIntersection(String intersectionId) 
                {
        Intersection intersection = intersectionRepository.findByIntersectionNumber(intersectionId)
                .orElseThrow(() -> {
                    log.error("Intersection not found for deletion: {}", intersectionId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Intersection not found: " + intersectionId);
                });

        // Delete in FK dependency order: intersection_organization → rsu_intersection →
        // intersections
        log.debug("Deleting relationship records for intersection {}", intersectionId);
        intersectionOrganizationRepository
                .deleteIntersectionOrganizationByIntersection_IntersectionNumber(intersectionId);
        rsuIntersectionRepository.deleteByIntersection_IntersectionNumber(intersectionId);
        intersectionRepository.delete(intersection);
        log.info("Successfully deleted intersection {}", intersectionId);
    }

    private Map<Integer, List<String>> loadRsuIpsByIntersection(List<String> intersectionNumbers) {
        List<RsuIntersectionRepository.IntersectionRsuProjection> projections = rsuIntersectionRepository
                .findRsuIpsByIntersectionNumbers(intersectionNumbers);
        log.debug("Retrieved {} RSU-intersection projection record(s).", projections.size());
        Map<Integer, List<String>> result = new HashMap<>();
        for (RsuIntersectionRepository.IntersectionRsuProjection proj : projections) {
            String ip = inetMapper.mapInetAddressToString(proj.getRsuIp());
            result.computeIfAbsent(proj.getIntersectionNumber(), _ -> new ArrayList<>()).add(ip);
        }
        return result;
    }

}
