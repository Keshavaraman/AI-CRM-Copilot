package com.keshava.crmai.security.filter;

import com.keshava.crmai.multitenancy.TenantContext;
import com.keshava.crmai.multitenancy.TenantDataSourceRouter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantDataSourceRouter tenantDataSourceRouter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId == null || tenantId.isBlank()) {
            response.sendError(HttpStatus.BAD_REQUEST.value(),
                    "Missing required header: " + TENANT_HEADER);
            return;
        }

        if (!tenantDataSourceRouter.hasTenant(tenantId)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(),
                    "Unknown tenant: " + tenantId);
            return;
        }

        try {
            TenantContext.setCurrentTenant(tenantId);
            log.debug("Tenant context set: {}", tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            log.debug("Tenant context cleared");
        }
    }
}
