package us.dot.its.jpo.ode.api.services;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import us.dot.its.jpo.ode.api.mappers.GeometryMapper;
import us.dot.its.jpo.ode.api.mappers.INetMapper;
import us.dot.its.jpo.ode.api.mappers.IntersectionMapper;
import us.dot.its.jpo.ode.api.models.admin.intersection.AllowedSelections;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionDto;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionListResponse;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionPatch;
import us.dot.its.jpo.ode.api.models.admin.intersection.IntersectionSingleResponse;
import us.dot.its.jpo.ode.api.models.postgres.tables.Intersection;
import us.dot.its.jpo.ode.api.models.postgres.tables.IntersectionOrganization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Organization;
import us.dot.its.jpo.ode.api.models.postgres.tables.Rsu;
import us.dot.its.jpo.ode.api.models.postgres.tables.RsuIntersection;
import us.dot.its.jpo.ode.api.repositories.IntersectionOrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.OrganizationRepository;
import us.dot.its.jpo.ode.api.repositories.RsuIntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for admin intersection management.
 *
 * This service is responsible only for business logic and repository operations. All authorization
 * (role checks, intersection resource access, and org restriction enforcement)
 * is handled by AdminIntersectionController before this service is called.
 *
 * Org-filtering and allowed-selections context (isSuperUser, userOrgs, operatorOrgs)
 * is computed by the controller from the auth token and passed in as parameters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminIntersectionService {

    private final IntersectionRepository intersectionRepository;
    private final IntersectionOrganizationRepository intersectionOrganizationRepository;
    private final RsuIntersectionRepository rsuIntersectionRepository;
    private final OrganizationRepository organizationRepository;
    private final RsuRepository rsuRepository;
    private final IntersectionMapper intersectionMapper;
    private final INetMapper inetMapper;
    private final GeometryMapper geometryMapper;

    /**
     * Returns a single intersection by intersection_number, plus allowed_selections for UI dropdowns.
     * Applies org filtering based on the requesting user's context.
     *
     * @param intersectionId the intersection_number to look up
     * @param organization   the scoped organization from the request header (may be null)
     * @param isSuperUser    whether the requesting user is a superuser
     * @param userOrgs       USER-role qualified orgs — used for intersection filtering
     * @param operatorOrgs   OPERATOR-role qualified orgs — used for allowed_selections
     * @return response containing intersection_data (empty IntersectionData if not found) and
     *         allowed_selections
     */
    public IntersectionSingleResponse getIntersection(String intersectionId, String organization,
            boolean isSuperUser, List<String> userOrgs, List<String> operatorOrgs) {
        log.info("Fetching intersection with id: {}, organization scope: {}, isSuperUser: {}", intersectionId, organization, isSuperUser);
        AllowedSelections allowedSelections = buildAllowedSelections(isSuperUser, operatorOrgs);

        Intersection intersection = intersectionRepository.findByIntersectionNumberWithOrgs(intersectionId)
          .orElseThrow(() -> {
              log.error("Intersection with id {} not found", intersectionId);
              return new EntityNotFoundException("Intersection with id " + intersectionId + " not found");
          });
        log.debug("Found intersection {}. Total org associations: {}", intersectionId, intersection.getIntersectionOrganizations().size());

        List<String> filteredOrgs = filterOrgNames(intersection, organization, isSuperUser, userOrgs);
        if (!isSuperUser && filteredOrgs.isEmpty()) {
            log.error("Access denied for user to intersection with id {}. User orgs: {}, organization scope: {}", intersectionId, userOrgs, organization);
            throw new AccessDeniedException("Access denied for intersection with id " + intersectionId);
        }

        IntersectionDto dto = intersectionMapper.toDto(intersection);
        dto.setOrganizations(filteredOrgs);

        List<String> rsuIps = rsuIntersectionRepository.findRsuIpsByIntersectionNumber(intersectionId)
                .stream()
                .map(inetMapper::mapInetAddressToString)
                .collect(Collectors.toList());
        dto.setRsus(rsuIps);

        log.debug("Successfully fetched intersection {}. Filtered orgs count: {}, RSUs count: {}", intersectionId, filteredOrgs.size(), rsuIps.size());
        return new IntersectionSingleResponse(dto, allowedSelections);
    }

    /**
     * Returns all intersections accessible to the requesting user, filtered by organization context.
     * Does NOT include allowed_selections.
     *
     * @param organization the scoped organization from the request header (may be null)
     * @param isSuperUser  whether the requesting user is a superuser
     * @param userOrgs     USER-role qualified orgs — used for intersection filtering
     * @return response containing intersection_data as a list (may be empty)
     */
    public IntersectionListResponse getAllIntersections(String organization, boolean isSuperUser, List<String> userOrgs) {
        log.info("Fetching all accessible intersections. Organization scope: {}, isSuperUser: {}, userOrgs: {}", organization, isSuperUser, userOrgs);
        List<Intersection> intersections = queryIntersections(organization, isSuperUser, userOrgs);

        if (intersections.isEmpty()) {
            log.warn("No accessible intersections found for organization scope '{}' and user orgs {}", organization, userOrgs);
            throw new EntityNotFoundException("No accessible intersections found for organization '" + organization + "' or organizations [" + userOrgs + "]");
        }

        List<IntersectionDto> dtos = intersections.stream()
                .map(intersectionMapper::toDto)
                .collect(Collectors.toList());

        List<String> intersectionNumbers = intersections.stream()
                .map(Intersection::getIntersectionNumber)
                .collect(Collectors.toList());

        Map<String, List<String>> rsusByIntersection = loadRsuIpsByIntersection(intersectionNumbers);
        log.debug("RSU IP mapping resolved for {}/{} intersections.", rsusByIntersection.size(), intersectionNumbers.size());

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
    public void patchIntersection(IntersectionPatch patch) {
        String origNumber = patch.getOrigIntersectionId().toString();
        String newNumber = patch.getIntersectionId().toString();

        log.info("Patching intersection. Original ID: {}, New ID: {}", origNumber, newNumber);

        Intersection intersection = intersectionRepository.findByIntersectionNumber(origNumber)
                .orElseThrow(() -> {
                    log.error("Intersection not found for patching: {}", origNumber);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Intersection not found: " + origNumber);
                });
        log.debug("Found intersection {} for patching.", origNumber);

        // Step 1: Update the intersection record
        log.debug("Step 1: Updating intersection base record fields. intersectionNumber={}, refPt={}, bbox={}, intersectionName={}, originIp={}",
                newNumber,
                patch.getRefPt(),
                patch.getBbox() != null ? "provided" : "unchanged",
                patch.getIntersectionName() != null ? patch.getIntersectionName() : "unchanged",
                patch.getOriginIp() != null ? patch.getOriginIp() : "unchanged");
        intersection.setIntersectionNumber(newNumber);
        intersection.setRefPt(geometryMapper.toPoint(patch.getRefPt()));
        if (patch.getBbox() != null) {
            intersection.setBbox(geometryMapper.toPolygon(patch.getBbox()));
        }
        if (patch.getIntersectionName() != null) {
            intersection.setIntersectionName(patch.getIntersectionName());
        }
        if (patch.getOriginIp() != null) {
            intersection.setOriginIp(inetMapper.mapStringToInetAddress(patch.getOriginIp()));
        }
        intersectionRepository.save(intersection);
        log.debug("Step 1: Intersection base record saved.");

        // Step 2: Add org associations
        if (!patch.getOrganizationsToAdd().isEmpty()) {
            log.debug("Step 2: Adding {} organization association(s): {}", patch.getOrganizationsToAdd().size(), patch.getOrganizationsToAdd());
            List<Organization> orgs = organizationRepository.findByNameIn(patch.getOrganizationsToAdd());
            if (orgs.size() != patch.getOrganizationsToAdd().size()) {
                log.warn("Step 2: Requested {} org(s) to add but only {} resolved in DB. Requested: {}", patch.getOrganizationsToAdd().size(), orgs.size(), patch.getOrganizationsToAdd());
            }
            List<IntersectionOrganization> newAssocs = orgs.stream()
                    .map(org -> {
                        IntersectionOrganization io = new IntersectionOrganization();
                        io.setIntersection(intersection);
                        io.setOrganization(org);
                        return io;
                    })
                    .collect(Collectors.toList());
            intersectionOrganizationRepository.saveAll(newAssocs);
            log.debug("Step 2: Saved {} org association(s).", newAssocs.size());
        } else {
            log.debug("Step 2: No org associations to add.");
        }

        // Step 3: Remove org associations
        if (!patch.getOrganizationsToRemove().isEmpty()) {
            log.debug("Step 3: Removing {} organization association(s): {}", patch.getOrganizationsToRemove().size(), patch.getOrganizationsToRemove());
            intersectionOrganizationRepository.deleteByIntersectionNumberAndOrganizationNameIn(
                    newNumber, patch.getOrganizationsToRemove());
            log.debug("Step 3: Org association removal complete.");
        } else {
            log.debug("Step 3: No org associations to remove.");
        }

        // Step 4: Add RSU associations
        if (!patch.getRsusToAdd().isEmpty()) {
            log.debug("Step 4: Adding {} RSU association(s): {}", patch.getRsusToAdd().size(), patch.getRsusToAdd());
            List<InetAddress> ipsToAdd = patch.getRsusToAdd().stream()
                    .map(inetMapper::mapStringToInetAddress)
                    .collect(Collectors.toList());
            List<Rsu> rsus = rsuRepository.findByIpv4AddressIn(ipsToAdd);
            if (rsus.size() != patch.getRsusToAdd().size()) {
                log.warn("Step 4: Requested {} RSU(s) to add but only {} resolved in DB. Requested: {}", patch.getRsusToAdd().size(), rsus.size(), patch.getRsusToAdd());
            }
            List<RsuIntersection> newRsuAssocs = rsus.stream()
                    .map(rsu -> {
                        RsuIntersection ri = new RsuIntersection();
                        ri.setIntersection(intersection);
                        ri.setRsu(rsu);
                        return ri;
                    })
                    .collect(Collectors.toList());
            rsuIntersectionRepository.saveAll(newRsuAssocs);
            log.debug("Step 4: Saved {} RSU association(s).", newRsuAssocs.size());
        } else {
            log.debug("Step 4: No RSU associations to add.");
        }

        // Step 5: Remove RSU associations
        if (!patch.getRsusToRemove().isEmpty()) {
            log.debug("Step 5: Removing {} RSU association(s): {}", patch.getRsusToRemove().size(), patch.getRsusToRemove());
            List<InetAddress> ipsToRemove = patch.getRsusToRemove().stream()
                    .map(inetMapper::mapStringToInetAddress)
                    .collect(Collectors.toList());
            rsuIntersectionRepository.deleteByIntersectionNumberAndRsuIpv4AddressIn(
                    newNumber, ipsToRemove);
            log.debug("Step 5: RSU association removal complete.");
        } else {
            log.debug("Step 5: No RSU associations to remove.");
        }
        log.info("Successfully patched intersection {}", origNumber);
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
    public void deleteIntersection(String intersectionId) {
        log.info("Deleting intersection with id: {}", intersectionId);
        Intersection intersection = intersectionRepository.findByIntersectionNumber(intersectionId)
                .orElseThrow(() -> {
                    log.error("Intersection not found for deletion: {}", intersectionId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Intersection not found: " + intersectionId);
                });

        // Delete in FK dependency order: intersection_organization → rsu_intersection → intersections
        log.debug("Deleting relationship records for intersection {}", intersectionId);
        intersectionOrganizationRepository
                .deleteIntersectionOrganizationByIntersection_IntersectionNumber(intersectionId);
        rsuIntersectionRepository.deleteByIntersection_IntersectionNumber(intersectionId);
        intersectionRepository.delete(intersection);
        log.info("Successfully deleted intersection {}", intersectionId);
    }

    private List<Intersection> queryIntersections(String organization, boolean isSuperUser,
            List<String> userOrgs) {
        if (organization != null) {
            log.debug("Querying intersections scoped to organization: {}", organization);
            List<Intersection> results = intersectionRepository.findAllByOrgNameWithOrgs(organization);
            log.debug("Found {} intersection(s) for organization: {}", results.size(), organization);
            return results;
        }
        if (isSuperUser) {
            log.debug("Querying all intersections (superuser path).");
            List<Intersection> results = intersectionRepository.findAllWithOrgs();
            log.debug("Found {} intersection(s) (superuser).", results.size());
            return results;
        }
        if (userOrgs.isEmpty()) {
            log.debug("No user orgs available; returning empty intersection list.");
            return Collections.emptyList();
        }
        log.debug("Querying intersections for {} user org(s): {}", userOrgs.size(), userOrgs);
        List<Intersection> results = intersectionRepository.findAllByOrgNamesWithOrgs(userOrgs);
        log.debug("Found {} intersection(s) for user orgs.", results.size());
        return results;
    }

    private Map<String, List<String>> loadRsuIpsByIntersection(List<String> intersectionNumbers) {
        log.debug("Loading RSU IPs for {} intersection(s).", intersectionNumbers.size());
        List<RsuIntersectionRepository.IntersectionRsuProjection> projections =
                rsuIntersectionRepository.findRsuIpsByIntersectionNumbers(intersectionNumbers);
        log.debug("Retrieved {} RSU-intersection projection record(s).", projections.size());
        Map<String, List<String>> result = new HashMap<>();
        for (RsuIntersectionRepository.IntersectionRsuProjection proj : projections) {
            String ip = inetMapper.mapInetAddressToString(proj.getRsuIp());
            result.computeIfAbsent(proj.getIntersectionNumber(), _ -> new ArrayList<>()).add(ip);
        }
        return result;
    }

    private List<String> filterOrgNames(Intersection intersection, String organization,
            boolean isSuperUser, List<String> userOrgs) {
        List<String> allOrgNames = intersection.getIntersectionOrganizations().stream()
                .filter(io -> io.getOrganization() != null)
                .map(io -> io.getOrganization().getName())
                .collect(Collectors.toList());
        log.debug("Intersection {} has {} org association(s) in DB.", intersection.getIntersectionNumber(), allOrgNames.size());

        if (isSuperUser) {
            log.debug("Superuser: returning all {} org(s) for intersection {}.", allOrgNames.size(), intersection.getIntersectionNumber());
            return allOrgNames;
        }
        if (organization != null) {
            List<String> filtered = allOrgNames.stream()
                    .filter(name -> name.equals(organization))
                    .collect(Collectors.toList());
            log.debug("Org-scoped filter (org={}): {}/{} org(s) matched for intersection {}.",
                    organization, filtered.size(), allOrgNames.size(), intersection.getIntersectionNumber());
            return filtered;
        }
        Set<String> userOrgSet = new HashSet<>(userOrgs);
        List<String> filtered = allOrgNames.stream()
                .filter(userOrgSet::contains)
                .collect(Collectors.toList());
        log.debug("User-orgs filter: {}/{} org(s) matched for intersection {}. User orgs: {}",
                filtered.size(), allOrgNames.size(), intersection.getIntersectionNumber(), userOrgs);
        return filtered;
    }

    private AllowedSelections buildAllowedSelections(boolean isSuperUser, List<String> operatorOrgs) {
        if (isSuperUser) {
            log.debug("Building allowed selections for superuser (all orgs + all RSUs).");
            List<String> allOrgs = organizationRepository.findAll().stream()
                    .map(Organization::getName)
                    .sorted()
                    .collect(Collectors.toList());
            List<String> allRsus = rsuRepository.findAll().stream()
                    .map(rsu -> inetMapper.mapInetAddressToString(rsu.getIpv4Address()))
                    .sorted()
                    .collect(Collectors.toList());
            log.debug("Allowed selections (superuser): {} org(s), {} RSU(s).", allOrgs.size(), allRsus.size());
            return new AllowedSelections(allOrgs, allRsus);
        }
        log.debug("Building allowed selections for {} operator org(s): {}", operatorOrgs.size(), operatorOrgs);
        List<String> allowedOrgs = new ArrayList<>(operatorOrgs);
        Collections.sort(allowedOrgs);
        List<String> allowedRsus = rsuRepository.findAllowedRsuIpsInOrganizations(operatorOrgs).stream()
                .map(inetMapper::mapInetAddressToString)
                .sorted()
                .collect(Collectors.toList());
        log.debug("Allowed selections (operator): {} org(s), {} RSU(s).", allowedOrgs.size(), allowedRsus.size());
        return new AllowedSelections(allowedOrgs, allowedRsus);
    }
}
