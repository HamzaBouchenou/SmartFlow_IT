package com.smartflow.backend.crosscutting.security;

import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * §6.1 - "Connexion par identifiant et mot de passe" : the identifier is the e-mail
 * (UserRepository.findByEmail). Loads every Role a user holds across all their
 * UserRoleAssignment rows (a user can hold several, at different scopes) into
 * SmartFlowUserDetails.
 */
@Service
public class SmartFlowUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public SmartFlowUserDetailsService(UserRepository userRepository, UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRepository = userRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides."));
        Set<Role> roles = userRoleAssignmentRepository.findByUserId(user.getId()).stream()
                .map(UserRoleAssignment::getRole)
                .collect(Collectors.toUnmodifiableSet());
        return new SmartFlowUserDetails(user, roles);
    }
}
