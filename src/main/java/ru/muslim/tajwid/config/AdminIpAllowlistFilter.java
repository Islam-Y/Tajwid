package ru.muslim.tajwid.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AdminIpAllowlistFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminIpAllowlistFilter.class);

    private final TajwidBotProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.getAdminSecurity().isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        List<String> allowedIps = properties.getAdminSecurity().getAllowedIps();
        if (allowedIps == null || allowedIps.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        boolean allowed = isAllowed(clientIp, allowedIps);
        if (!allowed) {
            log.warn("Rejected admin API request from IP {}", clientIp);
            response.sendError(HttpStatus.FORBIDDEN.value(), "IP is not allowed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String clientIp, List<String> allowedRanges) {
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }

        for (String range : allowedRanges) {
            if (!StringUtils.hasText(range)) {
                continue;
            }

            try {
                if (new IpAddressMatcher(range.trim()).matches(clientIp)) {
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid admin allowed IP range '{}'", range);
            }
        }
        return false;
    }
}
