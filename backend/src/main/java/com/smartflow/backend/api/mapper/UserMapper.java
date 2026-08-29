package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.LoginResponse;
import com.smartflow.backend.api.dto.response.UserResponse;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.Role;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserMapper {

    private UserMapper() {
    }

    public static LoginResponse toLoginResponse(User user, Set<Role> roles) {
        List<String> roleNames = roles.stream().map(Role::name).sorted().toList();
        return new LoginResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), roleNames);
    }

    /** §6.1/§6.10/§13 - jamais passwordHash. */
    public static UserResponse toResponse(User user, Instant now) {
        Department department = user.getDepartment();
        User manager = user.getManager();
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                department != null ? department.getId() : null, department != null ? department.getName() : null,
                manager != null ? manager.getId() : null, manager != null ? manager.getFirstName() + " " + manager.getLastName() : null,
                user.isActive(), locked);
    }
}
