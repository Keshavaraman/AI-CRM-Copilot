package com.keshava.crmai.security.entity;

import com.keshava.crmai.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Org-specific user record. id = same UUID as PlatformUser.id (cross-DB identity link).
 * Credentials (password) live only in platform_db. This entity holds org membership context.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity implements UserDetails {

    // Denormalized from platform_users for convenience — never used for auth
    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(nullable = false)
    private boolean active = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (profile == null) return List.of();
        return profile.getPermissions().stream()
                .map(p -> new SimpleGrantedAuthority(p.getModuleName() + ":" + p.getAction().name()))
                .toList();
    }

    // Username for Spring Security context = email (display identity)
    @Override
    public String getUsername() {
        return email;
    }

    // No password stored here — credentials are in platform_db
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
