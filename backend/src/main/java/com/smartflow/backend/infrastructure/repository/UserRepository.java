package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // §6.1 - "Connexion par identifiant et mot de passe" : l'identifiant est l'e-mail.
    Optional<User> findByEmail(String email);
}
