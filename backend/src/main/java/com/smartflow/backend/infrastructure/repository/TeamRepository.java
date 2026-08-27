package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByDepartmentId(Long departmentId);
}
