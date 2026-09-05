package com.smartflow.backend.api.controller;

import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.infrastructure.repository.NotificationPreferenceRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

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

    @Test
    @DisplayName("§6.8 - la liste des préférences porte les 7 types, activés par défaut faute de ligne en base (ADR-12)")
    void listsEveryNotificationTypeEnabledByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/profile/notification-preferences").with(user(asSelf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(NotificationType.values().length))
                .andExpect(jsonPath("$[?(@.notificationType=='ASSIGNMENT')].emailEnabled").value(true))
                .andExpect(jsonPath("$[?(@.notificationType=='ASSIGNMENT')].mandatory").value(false))
                .andExpect(jsonPath("$[?(@.notificationType=='SLA_WARNING')].mandatory").value(true));
    }

    @Test
    @DisplayName("§6.8 - un type non obligatoire se désactive et la préférence est bien persistée")
    void disablesOptionalNotificationType() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("emailEnabled", false));
        mockMvc.perform(put("/api/v1/profile/notification-preferences/{type}", "ASSIGNMENT")
                        .with(user(asSelf)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false));

        assertThat(notificationPreferenceRepository
                .findByUserIdAndNotificationType(self.getId(), NotificationType.ASSIGNMENT))
                .hasValueSatisfying(preference -> assertThat(preference.isEmailEnabled()).isFalse());

        mockMvc.perform(get("/api/v1/profile/notification-preferences").with(user(asSelf)))
                .andExpect(jsonPath("$[?(@.notificationType=='ASSIGNMENT')].emailEnabled").value(false));
    }

    @Test
    @DisplayName("§6.8/ADR-12 - une alerte obligatoire (SLA_WARNING) ne peut pas être désactivée, et le dit explicitement")
    void refusesToDisableMandatoryNotificationType() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("emailEnabled", false));
        mockMvc.perform(put("/api/v1/profile/notification-preferences/{type}", "SLA_WARNING")
                        .with(user(asSelf)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_TYPE_MANDATORY"));

        // Rien n'a été écrit : le refus doit être total, pas une ligne posée puis ignorée.
        assertThat(notificationPreferenceRepository
                .findByUserIdAndNotificationType(self.getId(), NotificationType.SLA_WARNING)).isEmpty();
    }
}
