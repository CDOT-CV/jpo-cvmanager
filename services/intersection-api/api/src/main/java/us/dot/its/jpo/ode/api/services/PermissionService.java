package us.dot.its.jpo.ode.api.services;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import us.dot.its.jpo.ode.api.models.keycloak.DecodedToken;
import us.dot.its.jpo.ode.api.repositories.IntersectionRepository;
import us.dot.its.jpo.ode.api.repositories.RsuRepository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service("PermissionService")
@RequiredArgsConstructor
public class PermissionService {

    private final IntersectionRepository intersectionRepository;
    private final RsuRepository rsuRepository;

    private static final Map<String, Integer> ROLE_HIERARCHY = new HashMap<>();

    static {
        ROLE_HIERARCHY.put("ADMIN", 3);
        ROLE_HIERARCHY.put("OPERATOR", 2);
        ROLE_HIERARCHY.put("USER", 1);
    }

    public static boolean checkRoleAbove(String userRole, String requiredRole) {
        if (userRole == null) {
            return false;
        }
        List<String> roles = List.of("USER", "OPERATOR", "ADMIN");
        return roles.indexOf(userRole.toUpperCase()) >= roles.indexOf(requiredRole.toUpperCase());
    }

    public List<Integer> getAllowedIntersectionIdsByEmail(String email) {
        return intersectionRepository.findAllowedIntersectionIdsByEmail(email).stream().map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    public List<Integer> getAllowedIntersectionIdsByOrganization(String email) {
        return intersectionRepository.findIntersectionsByOrganization(email).stream().map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    // Allow Connection if the user is a SuperUser
    public boolean isSuperUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthValid(auth)) {
            return false;
        }

        DecodedToken decodedToken = DecodedToken.fromJwtToken(getJwtTokenFromRequest());

        return decodedToken.isSuperUser();
    }

    // Allow Connection if the user is a part of at least one organization with a
    // matching roll.
    public boolean hasRole(String role) {
        System.out.println("HAS_ROLE role: " + role);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthValid(auth)) {
            System.out.println("- HAS_ROLE auth invalid");
            return false;
        }

        DecodedToken decodedToken = DecodedToken.fromJwtToken(getJwtTokenFromRequest());
        if (decodedToken.isSuperUser()) {
            System.out.println("- HAS_ROLE is super user");
            return true;
        }

        String organization = getOrganizationFromHeader();
        if (organization != null) {
            Optional<String> userRole = decodedToken.findRoleInOrg(organization);
            System.out.println("- HAS_ROLE org header specified");
            System.out.println("  - HAS_ROLE userRole: " + userRole.orElse("NULL"));
            System.out.println("  - HAS_ROLE checkRoleAbove " + checkRoleAbove(userRole.orElse(""), role));
            return userRole.map(roleValue -> checkRoleAbove(roleValue, role)).orElse(false);
        }
        System.out.println("- HAS_ROLE no org header");
        System.out.println("  - HAS_ROLE qualified orgs: " + String.join(", ", decodedToken.getQualifiedOrgList(role)));
        return !decodedToken.getQualifiedOrgList(role).isEmpty();
    }

    // Allow Connection if the users organization controls the specified
    // intersection
    public boolean hasIntersection(Integer intersectionID, String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthValid(auth)) {
            return false;
        }

        // Must be null check first, otherwise throws null pointer exception (if null)
        if (intersectionID == null || intersectionID == -1) {
            return true;
        }

        DecodedToken decodedToken = DecodedToken.fromJwtToken(getJwtTokenFromRequest());
        if (decodedToken.isSuperUser()) {
            return true;
        }

        List<String> qualifiedOrgs = decodedToken.getQualifiedOrgList(role);

        String organization = getOrganizationFromHeader();
        if (organization != null) {
            if (qualifiedOrgs.contains(organization)) {
                return intersectionRepository.existsByIdAndOrganizations(intersectionID.toString(),
                        List.of(organization));
            } else {
                return false;
            }
        }

        return intersectionRepository.existsByIdAndOrganizations(intersectionID.toString(), qualifiedOrgs);
    }

    // Allow Connection if the users organization controls the specified RSU unit
    public boolean hasRsu(String rsuIP, String role) {
        InetAddress ipv4Address;
        try {
            ipv4Address = InetAddress.getByName(rsuIP);
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid RSU IP address: " + rsuIP, e);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthValid(auth)) {
            return false;
        }

        DecodedToken decodedToken = DecodedToken.fromJwtToken(getJwtTokenFromRequest());
        if (decodedToken.isSuperUser()) {
            return true;
        }

        List<String> qualifiedOrgs = decodedToken.getQualifiedOrgList(role);

        String organization = getOrganizationFromHeader();
        if (organization != null) {
            if (qualifiedOrgs.contains(organization)) {
                return rsuRepository.existsByIpAndOrganizations(ipv4Address, List.of(organization));
            } else {
                return false;
            }
        }

        return rsuRepository.existsByIpAndOrganizations(ipv4Address, qualifiedOrgs);
    }

    // Allow Connection if the users organization controls the specified RSU unit
    public boolean hasRsus(List<String> rsuIP, String role) {
        List<InetAddress> ipv4Addresses = new ArrayList<>();
        for (String ip : rsuIP) {
            try {
                ipv4Addresses.add(InetAddress.getByName(ip));
            } catch (UnknownHostException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid RSU IP address: " + ip, e);
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthValid(auth)) {
            return false;
        }

        DecodedToken decodedToken = DecodedToken.fromJwtToken(getJwtTokenFromRequest());
        if (decodedToken.isSuperUser()) {
            return true;
        }

        List<String> qualifiedOrgs = decodedToken.getQualifiedOrgList(role);

        List<InetAddress> allowedRsuIps = rsuRepository.findAllowedRsuIpsInOrganizations(qualifiedOrgs);
        return allowedRsuIps.containsAll(ipv4Addresses);
    }

    // helper method to make sure authentication is valid
    public boolean isAuthValid(Authentication auth) {
        if (!auth.isAuthenticated()) {
            return false;
        }

        return auth instanceof JwtAuthenticationToken;
    }

    public static String getUsername(Authentication auth) {
        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth;
        return jwtAuth.getToken().getClaimAsString("preferred_username");
    }

    public static String getOrganizationFromHeader() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String organization = null;
        if (attributes != null) {
            organization = attributes.getRequest().getHeader("Organization");
        }
        return organization;
    }

    /**
     * Extracts the JWT token string from the Authorization header.
     *
     * @return The JWT token string (never {@code null}).
     * @throws ResponseStatusException if the request context is unavailable or the
     *                                 Authorization header is missing or invalid.
     */
    public static String getJwtTokenFromRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "No request context available to extract JWT token");
        }

        String authHeader = attributes.getRequest().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header");
        }

        return authHeader.substring(7);
    }
}