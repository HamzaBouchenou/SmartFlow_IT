package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.LoginResponse;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.Role;

import java.util.List;
import java.util.Set;

public final class UserMapper {

    private UserMapper() {
    }

    public static LoginResponse toLoginResponse(User user, Set<Role> roles) {
        List<String> roleNames = roles.stream().map(Role::name).sorted().toList();
        return new LoginResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), roleNames);
    }
}
