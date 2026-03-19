package us.dot.its.jpo.ode.api.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for admin intersection management.
 *
 * This service is responsible only for database operations. All authorization
 * (role checks, intersection resource access, and org restriction enforcement)
 * is handled by AdminIntersectionController before this service is called.
 *
 * Org-filtering and allowed-selections context (isSuperUser, userOrgs, operatorOrgs)
 * is computed by the controller from the auth token and passed in as parameters.
 */
@Service
@RequiredArgsConstructor
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
     * Applies org filtering based on the requesting user's context (see spec: Organization Filtering).
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
        AllowedSelections allowedSelections = buildAllowedSelections(isSuperUser, operatorOrgs);

        Optional<Intersection> opt = intersectionRepository.findByIntersectionNumberWithOrgs(intersectionId);
        if (opt.isEmpty()) {
            return new IntersectionSingleResponse(new IntersectionDto(), allowedSelections);
        }

        Intersection intersection = opt.get();

        List<String> filteredOrgs = filterOrgNames(intersection, organization, isSuperUser, userOrgs);
        if (!isSuperUser && filteredOrgs.isEmpty()) {
            return new IntersectionSingleResponse(new IntersectionDto(), allowedSelections);
        }

        IntersectionDto dto = intersectionMapper.toDto(intersection);
        dto.setOrganizations(filteredOrgs);

        List<String> rsuIps = rsuIntersectionRepository.findRsuIpsByIntersectionNumber(intersectionId)
                .stream()
                .map(inetMapper::mapInetAddressToString)
                .collect(Collectors.toList());
        dto.setRsus(rsuIps);

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
    public IntersectionListResponse getAllIntersections(String organization, boolean isSuperUser,
            List<String> userOrgs) {
        List<Intersection> intersections = queryIntersections(organization, isSuperUser, userOrgs);

        if (intersections.isEmpty()) {
            return new IntersectionListResponse(Collections.emptyList());
        }

        List<IntersectionDto> dtos = intersections.stream()
                .map(intersectionMapper::toDto)
                .collect(Collectors.toList());

        List<String> intersectionNumbers = intersections.stream()
                .map(Intersection::getIntersectionNumber)
                .collect(Collectors.toList());

        Map<String, List<String>> rsusByIntersection = loadRsuIpsByIntersection(intersectionNumbers);

        for (IntersectionDto dto : dtos) {
            dto.setRsus(rsusByIntersection.getOrDefault(dto.getIntersectionId(), Collections.emptyList()));
        }

        return new IntersectionListResponse(dtos);
    }

    /**
     * Updates an intersection's properties and modifies its org/RSU relationships.
     * All authorization has already been enforced by the controller before this is called.
     * Wraps all writes in a single transaction (fixes known issue #1).
     *
     * @param patch the patch request body
     * @return success message
     */
    @Transactional
    public String patchIntersection(IntersectionPatch patch) {
        String origNumber = patch.getOrigIntersectionId().toString();
        String newNumber = patch.getIntersectionId().toString();

        Intersection intersection = intersectionRepository.findByIntersectionNumber(origNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Intersection not found: " + origNumber));

        // Step 1: Update the intersection record
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

        // Step 2: Add org associations
        if (!patch.getOrganizationsToAdd().isEmpty()) {
            List<Organization> orgs = organizationRepository.findByNameIn(patch.getOrganizationsToAdd());
            List<IntersectionOrganization> newAssocs = orgs.stream()
                    .map(org -> {
                        IntersectionOrganization io = new IntersectionOrganization();
                        io.setIntersection(intersection);
                        io.setOrganization(org);
                        return io;
                    })
                    .collect(Collectors.toList());
            intersectionOrganizationRepository.saveAll(newAssocs);
        }

        // Step 3: Remove org associations
        if (!patch.getOrganizationsToRemove().isEmpty()) {
            intersectionOrganizationRepository.deleteByIntersectionNumberAndOrganizationNameIn(
                    newNumber, patch.getOrganizationsToRemove());
        }

        // Step 4: Add RSU associations
        if (!patch.getRsusToAdd().isEmpty()) {
            List<InetAddress> ipsToAdd = patch.getRsusToAdd().stream()
                    .map(inetMapper::mapStringToInetAddress)
                    .collect(Collectors.toList());
            List<Rsu> rsus = rsuRepository.findByIpv4AddressIn(ipsToAdd);
            List<RsuIntersection> newRsuAssocs = rsus.stream()
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
                    newNumber, ipsToRemove);
        }

        return "Intersection successfully modified";
    }

    /**
     * Deletes an intersection and all its relationship records.
     * All authorization has already been enforced by the controller before this is called.
     * Wraps all writes in a single transaction (fixes known issue #1).
     * Throws 404 if the intersection does not exist (fixes known issue #2).
     *
     * @param intersectionId the intersection_number to delete
     * @return success message
     */
    @Transactional
    public String deleteIntersection(String intersectionId) {
        Intersection intersection = intersectionRepository.findByIntersectionNumber(intersectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Intersection not found: " + intersectionId));

        // Delete in FK dependency order: intersection_organization → rsu_intersection → intersections
        intersectionOrganizationRepository
                .deleteIntersectionOrganizationByIntersection_IntersectionNumber(intersectionId);
        rsuIntersectionRepository.deleteByIntersection_IntersectionNumber(intersectionId);
        intersectionRepository.delete(intersection);

        return "Intersection successfully deleted";
    }

    // ==================== Private Helpers ====================

    private List<Intersection> queryIntersections(String organization, boolean isSuperUser,
            List<String> userOrgs) {
        if (organization != null) {
            return intersectionRepository.findAllByOrgNameWithOrgs(organization);
        }
        if (isSuperUser) {
            return intersectionRepository.findAllWithOrgs();
        }
        if (userOrgs.isEmpty()) {
            return Collections.emptyList();
        }
        return intersectionRepository.findAllByOrgNamesWithOrgs(userOrgs);
    }

    private Map<String, List<String>> loadRsuIpsByIntersection(List<String> intersectionNumbers) {
        List<RsuIntersectionRepository.IntersectionRsuProjection> projections =
                rsuIntersectionRepository.findRsuIpsByIntersectionNumbers(intersectionNumbers);
        Map<String, List<String>> result = new HashMap<>();
        for (RsuIntersectionRepository.IntersectionRsuProjection proj : projections) {
            String ip = inetMapper.mapInetAddressToString(proj.getRsuIp());
            result.computeIfAbsent(proj.getIntersectionNumber(), k -> new ArrayList<>()).add(ip);
        }
        return result;
    }

    private List<String> filterOrgNames(Intersection intersection, String organization,
            boolean isSuperUser, List<String> userOrgs) {
        List<String> allOrgNames = intersection.getIntersectionOrganizations().stream()
                .filter(io -> io.getOrganization() != null)
                .map(io -> io.getOrganization().getName())
                .collect(Collectors.toList());

        if (isSuperUser) {
            return allOrgNames;
        }
        if (organization != null) {
            return allOrgNames.stream()
                    .filter(name -> name.equals(organization))
                    .collect(Collectors.toList());
        }
        Set<String> userOrgSet = new HashSet<>(userOrgs);
        return allOrgNames.stream()
                .filter(userOrgSet::contains)
                .collect(Collectors.toList());
    }

    private AllowedSelections buildAllowedSelections(boolean isSuperUser, List<String> operatorOrgs) {
        if (isSuperUser) {
            List<String> allOrgs = organizationRepository.findAll().stream()
                    .map(Organization::getName)
                    .sorted()
                    .collect(Collectors.toList());
            List<String> allRsus = rsuRepository.findAll().stream()
                    .map(rsu -> inetMapper.mapInetAddressToString(rsu.getIpv4Address()))
                    .sorted()
                    .collect(Collectors.toList());
            return new AllowedSelections(allOrgs, allRsus);
        }
        List<String> allowedOrgs = new ArrayList<>(operatorOrgs);
        Collections.sort(allowedOrgs);
        List<String> allowedRsus = rsuRepository.findAllowedRsuIpsInOrganizations(operatorOrgs).stream()
                .map(inetMapper::mapInetAddressToString)
                .sorted()
                .collect(Collectors.toList());
        return new AllowedSelections(allowedOrgs, allowedRsus);
    }
}
