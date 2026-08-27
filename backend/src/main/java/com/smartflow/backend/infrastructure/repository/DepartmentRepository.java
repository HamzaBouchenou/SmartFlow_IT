package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // §4.1 - navigation de l'organisation par direction (parent = null) puis service.
    List<Department> findByParentIsNull();

    List<Department> findByParentId(Long parentId);
}
