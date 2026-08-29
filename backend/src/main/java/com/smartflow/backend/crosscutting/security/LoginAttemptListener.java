package com.smartflow.backend.crosscutting.security;

import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * ADR-13 (docs/DECISIONS.md) - §13 "limitation des tentatives". Listens to Spring
 * Security's own authentication events rather than being called from AuthController: these
 * events fire for every AuthenticationProvider Spring Security ever consults, so a future
 * second login path (§6.1 - Azure AD/Entra ID en option) is covered for free, without
 * AuthController having to know this mechanism exists.
 *
 * Reacts only to AuthenticationFailureBadCredentialsEvent (wrong password on a real
 * account) - an already-locked account instead fires
 * AuthenticationFailureLockedException's own event, which this class ignores: the counter
 * and lock are already set, re-registering a failure while locked would only extend a
 * lockout window ADR-13 fixed at a flat duration, not a sliding one.
 */
@Component
public class LoginAttemptListener {

    public static final String MAX_ATTEMPTS_KEY = "security.login.max-attempts";
    public static final String LOCKOUT_MINUTES_KEY = "security.login.lockout-minutes";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final SystemParameterRepository systemParameterRepository;
    private final Clock clock;

    public LoginAttemptListener(UserRepository userRepository, SystemParameterRepository systemParameterRepository, Clock clock) {
        this.userRepository = userRepository;
        this.systemParameterRepository = systemParameterRepository;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication().getPrincipal() instanceof SmartFlowUserDetails details)) {
            return;
        }
        User user = details.getUser();
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    @EventListener
    @Transactional
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        registerFailure(event);
    }

    private void registerFailure(AbstractAuthenticationFailureEvent event) {
        // Pre-authentication, the principal is still the raw e-mail string the client
        // submitted (UsernamePasswordAuthenticationToken), never a UserDetails.
        String email = String.valueOf(event.getAuthentication().getPrincipal());
        userRepository.findByEmail(email).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= configuredMaxAttempts()) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(clock.instant().plus(Duration.ofMinutes(configuredLockoutMinutes())));
            } else {
                user.setFailedLoginAttempts(attempts);
            }
            userRepository.save(user);
        });
    }

    private int configuredMaxAttempts() {
        return systemParameterRepository.findByKey(MAX_ATTEMPTS_KEY)
                .map(parameter -> Integer.parseInt(parameter.getValue()))
                .orElse(DEFAULT_MAX_ATTEMPTS);
    }

    private int configuredLockoutMinutes() {
        return systemParameterRepository.findByKey(LOCKOUT_MINUTES_KEY)
                .map(parameter -> Integer.parseInt(parameter.getValue()))
                .orElse(DEFAULT_LOCKOUT_MINUTES);
    }
}
