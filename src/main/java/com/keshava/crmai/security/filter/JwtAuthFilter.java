package com.keshava.crmai.security.filter;

import com.keshava.crmai.multitenancy.TenantContext;
import com.keshava.crmai.security.service.JwtService;
import com.keshava.crmai.security.service.SessionService;
import com.keshava.crmai.security.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        try {
            if (sessionService.isRevoked(jwt)) {
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Token has been revoked");
                return;
            }

            String userId   = jwtService.extractUserId(jwt);
            String jwtTenantId    = jwtService.extractTenantId(jwt);
            String headerTenantId = TenantContext.getCurrentTenant();

            // Cross-check: header tenant must match the tenant in the JWT
            if (headerTenantId != null && !headerTenantId.equals(jwtTenantId)) {
                log.warn("Tenant mismatch — header: {}, JWT: {}", headerTenantId, jwtTenantId);
                response.sendError(HttpStatus.FORBIDDEN.value(), "Tenant mismatch");
                return;
            }

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            log.warn("JWT processing error: {}", e.getMessage());
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid token");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
