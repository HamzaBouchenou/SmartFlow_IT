package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.SystemParameter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemParameterRepository extends JpaRepository<SystemParameter, Long> {

    // §6.10 - paramètres généraux (formats acceptés, taille max, durées, seuils) et RG-08
    // (durée paramétrable de réouverture), lus par clé.
    Optional<SystemParameter> findByKey(String key);
}
