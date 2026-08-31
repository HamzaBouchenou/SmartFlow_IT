package com.smartflow.backend.api.controller;

import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.1 - libre-service : mise à jour du profil et changement de mot de passe, sur soi-même. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User self;
    private UserDetails asSelf;

    @BeforeEach
    void seed() {
        self = userRepository.save(new User("Amina", "Idrissi", "amina.profile@example.com", passwordEncoder.encode("OldPass123")));
        asSelf = new com.smartflow.backend.crosscutting.security.SmartFlowUserDetails(self, java.util.Set.of(com.smartflow.backend.domain.enums.Role.REQUESTER));
    }

    @Test
    @DisplayName("PUT/POST /profile require authentication")
    void requireAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/profile").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/profile/password").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§6.1 - un utilisateur met à jour son propre prénom/nom")
    void updatesOwnProfile() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("firstName", "Amina Fatima", "lastName", "Idrissi"));
        mockMvc.perform(put("/api/v1/profile").with(user(asSelf)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Amina Fatima"));

        assertThat(userRepository.findById(self.getId()).orElseThrow().getFirstName()).isEqualTo("Amina Fatima");
    }

    @Test
    @DisplayName("§6.1/§13 - changer son mot de passe exige l'ancien, sinon rejeté")
    void changePasswordRequiresCurrentPassword() throws Exception {
        String wrongCurrent = objectMapper.writeValueAsString(Map.of("currentPassword", "WrongPass", "newPassword", "NewPass123"));
        mockMvc.perform(post("/api/v1/profile/password").with(user(asSelf)).with(csrf())
                        .contentType("application/json").content(wrongCurrent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        String correctCurrent = objectMapper.writeValueAsString(Map.of("currentPassword", "OldPass123", "newPassword", "NewPass123"));
        mockMvc.perform(post("/api/v1/profile/password").with(user(asSelf)).with(csrf())
                        .contentType("application/json").content(correctCurrent))
                .andExpect(status().isNoContent());

        User reloaded = userRepository.findById(self.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass123", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPass123", reloaded.getPasswordHash())).isFalse();
    }
}
