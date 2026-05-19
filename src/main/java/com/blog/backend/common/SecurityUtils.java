package com.blog.backend.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static String currentUsername() {
        Authentication authentication = currentAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof String username && !"anonymousUser".equals(username)) {
            return username;
        }
        return null;
    }

    public static boolean hasRole(String role) {
        Authentication authentication = currentAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String target = "ROLE_" + role;
        return authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(authority -> target.equals(authority.getAuthority()));
    }
}
