package com.blog.backend.common;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = normalize(request.getHeader("X-Forwarded-For"));
        if (ip == null) {
            ip = normalize(request.getHeader("X-Real-IP"));
        }
        if (ip == null) {
            ip = normalize(request.getRemoteAddr());
        }
        if (ip == null) {
            return null;
        }
        int commaIndex = ip.indexOf(',');
        return commaIndex >= 0 ? ip.substring(0, commaIndex).trim() : ip;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }
}
