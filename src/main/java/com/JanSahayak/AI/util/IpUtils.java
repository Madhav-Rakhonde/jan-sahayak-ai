package com.JanSahayak.AI.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class IpUtils {

    /**
     * Extracts the IP from the current Spring RequestContext.
     * Useful for capturing IP directly in Service layers without modifying all controller signatures.
     */
    public static String getClientIpFromContext() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return extractIp(request);
        }
        return null;
    }

    /**
     * Extracts the true client IP address from the request.
     * Checks common proxy headers (like X-Forwarded-For from AWS ALB/Nginx) before falling back
     * to the direct socket IP.
     */
    public static String extractIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String[] headersToCheck = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headersToCheck) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain a comma-separated list of IPs.
                // The first one is the true client IP.
                if (ip.contains(",")) {
                    return ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }
}
