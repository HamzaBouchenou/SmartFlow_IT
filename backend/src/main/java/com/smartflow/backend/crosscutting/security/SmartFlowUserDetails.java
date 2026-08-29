package com.smartflow.backend.crosscutting.security;

import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security's view of a User (§6.1). Wraps the domain entity rather than duplicating
 * its fields, so application/security's canAct - which takes the domain User, not this
 * class - can be handed getUser() directly (e.g. from @AuthenticationPrincipal in a future
 * controller).
 *
 * getAuthorities() is a Spring Security housekeeping concern (framework plumbing, e.g. a
 * possible future hasRole() on an ops endpoint) and is deliberately NOT how business
 * authorization is decided - CLAUDE.md: "Ne disperse jamais ces règles dans des
 * @PreAuthorize dupliqués." Every per-resource decision goes through
 * application/security/AuthorizationService.canAct, which reads getRoles() (and the
 * request's own data) directly, not this class's granted authorities.
 */
public class SmartFlowUserDetails implements UserDetails {

    private final User user;
    private final Set<Role> roles;

    public SmartFlowUserDetails(User user, Set<Role> roles) {
        this.user = user;
        this.roles = roles;
    }

    public User getUser() {
        return user;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonLocked() {
        // ADR-13 (docs/DECISIONS.md) - crosscutting/security/LoginAttemptListener sets
        // User.lockedUntil on the max-th failed attempt; Instant.now() rather than an
        // injected Clock because this class, like a JPA @PrePersist callback, is a plain
        // object built per authentication, not a Spring bean that could receive one.
        Instant lockedUntil = user.getLockedUntil();
        return lockedUntil == null || Instant.now().isAfter(lockedUntil);
    }

    @Override
    public boolean isEnabled() {
        // §6.1 - "désactivation d'un compte" : a deactivated User must not be able to log in.
        return user.isActive();
    }
}
