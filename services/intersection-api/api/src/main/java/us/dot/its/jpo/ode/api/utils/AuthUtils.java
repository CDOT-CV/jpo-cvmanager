package us.dot.its.jpo.ode.api.utils;

import java.util.Map;

public class AuthUtils {

    private static final Map<String, Integer> ROLE_HIERARCHY = Map.of(
        "ADMIN", 3,
        "OPERATOR", 2,
        "USER", 1
    );

    public static boolean checkRoleAbove(String userRole, String requiredRole) {
        if (userRole == null || requiredRole == null) {
            return false;
        }
        return ROLE_HIERARCHY.getOrDefault(userRole.toUpperCase(), 0) >= ROLE_HIERARCHY.getOrDefault(requiredRole.toUpperCase(), 0);
    }
}
