package com.keshava.crmai.security.service;

import com.keshava.crmai.common.exception.AppException;
import com.keshava.crmai.multitenancy.TenantContext;
import com.keshava.crmai.platform.entity.PlatformUser;
import com.keshava.crmai.platform.repository.OrganizationMemberRepository;
import com.keshava.crmai.platform.repository.OrganizationRepository;
import com.keshava.crmai.platform.repository.PlatformUserRepository;
import com.keshava.crmai.security.dto.AuthRequest;
import com.keshava.crmai.security.dto.AuthResponse;
import com.keshava.crmai.security.dto.SessionInfo;
import com.keshava.crmai.security.entity.User;
import com.keshava.crmai.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PlatformUserRepository platformUserRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;

    public AuthResponse login(AuthRequest request, HttpServletRequest httpRequest) {
        String tenantId = TenantContext.getCurrentTenant();

        // Step 1 — verify credentials in platform_db (shared across all orgs)
        PlatformUser platformUser = platformUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!platformUser.isActive()) {
            throw new AppException("Account is disabled", HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(request.password(), platformUser.getPassword())) {
            throw new AppException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        // Step 2 — verify user is a member of this org
        var org = organizationRepository.findBySubdomain(tenantId)
                .orElseThrow(() -> new AppException("Unknown tenant", HttpStatus.BAD_REQUEST));

        boolean isMember = organizationMemberRepository
                .existsByPlatformUserIdAndOrganizationIdAndActiveTrue(platformUser.getId(), org.getId());

        if (!isMember) {
            throw new AppException("User is not a member of this organization", HttpStatus.FORBIDDEN);
        }

        // Step 3 — load tenant-specific user record (profile / permissions)
        User tenantUser = userRepository.findById(platformUser.getId())
                .orElseThrow(() -> new AppException("User not configured in this organization", HttpStatus.FORBIDDEN));

        String token = jwtService.generateToken(tenantUser, tenantId);

        sessionService.store(token, new SessionInfo(
                platformUser.getId().toString(),
                platformUser.getEmail(),
                tenantId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                Instant.now(),
                Instant.now().plusMillis(86400000L)
        ));

        return new AuthResponse(token, platformUser.getEmail(), platformUser.getId().toString(), tenantId, 86400000L);
    }
}
