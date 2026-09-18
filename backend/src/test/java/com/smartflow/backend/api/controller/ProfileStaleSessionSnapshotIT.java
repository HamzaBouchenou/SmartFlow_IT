package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §6.1/§5.1/ADR-13 - un geste de libre-service sur son propre profil ne doit jamais réécrire
 * ce qu'un administrateur a changé sur ce compte depuis la connexion.
 *
 * Pourquoi cette classe existe à part de ProfileControllerIT. Celle-ci est {@code
 * @Transactional} : le contexte de persistance du test est le même que celui du contrôleur
 * appelé par MockMvc, si bien que le principal y est une entité *gérée* et que le défaut que
 * ces tests-ci reproduisent y est structurellement invisible. En conditions réelles, le
 * principal vient de la session HTTP (ADR-01) : c'est un cliché **détaché**, figé à l'instant
 * de la connexion. Ces tests s'exécutent donc sans transaction de test, et construisent le
 * principal à partir d'une instance relue séparément - exactement la forme qu'a une session.
 *
 * Ce qu'ils prouvent. `save()` sur une entité détachée est un `merge` : il recopie *toutes*
 * les colonnes du cliché, pas seulement celles que le cas d'usage a modifiées. Avant
 * ProfileService.reload, enregistrer son propre prénom annulait donc en silence une
 * désactivation de compte (§6.1), un changement de service (§5.1 - donc de périmètre
 * d'autorisation), ou un verrou de connexion (ADR-13) - et l'`audit_log` affirmait alors
 * l'inverse de ce que la ligne contenait.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ProfileStaleSessionSnapshotIT {

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
    private DepartmentRepository departmentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    /**
     * Les vérifications lisent la ligne en SQL direct, jamais à travers le contexte de
     * persistance : ces tests portent précisément sur ce que `merge` écrit en base, donc
     * l'affirmation doit venir de la base et d'aucun cliché intermédiaire.
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Department originalService;
    private Department otherService;

    @BeforeEach
    void seed() {
        String unique = String.valueOf(System.nanoTime());
        Department direction = departmentRepository.save(new Department("Direction " + unique, null));
        originalService = departmentRepository.save(new Department("Service origine " + unique, direction));
        otherService = departmentRepository.save(new Department("Service cible " + unique, direction));

        User created = new User("Amina", "Idrissi", "amina.stale." + unique + "@example.com",
                passwordEncoder.encode("OldPass123"));
        created.setDepartment(originalService);
        userId = userRepository.save(created).getId();
    }

    /**
     * Le cliché que porterait la session : une instance relue puis laissée détachée, donc
     * figée sur les valeurs de l'instant de la "connexion". Chaque appel de findById hors
     * transaction renvoie une instance distincte - c'est ce qui permet de faire diverger le
     * cliché et la ligne, comme le temps les fait diverger en production.
     */
    private UserDetails sessionSnapshot() {
        User snapshot = userRepository.findById(userId).orElseThrow();
        return new SmartFlowUserDetails(snapshot, Set.of(Role.REQUESTER));
    }

    /** Une colonne de la ligne `users` de ce compte, lue en SQL direct. */
    private <T> T column(String name, Class<T> type) {
        return jdbcTemplate.queryForObject("select " + name + " from users where id = ?", type, userId);
    }

    @Test
    @DisplayName("§6.1 - enregistrer son propre nom ne réactive pas un compte désactivé entre-temps par un administrateur")
    void savingOwnNameNeverRevivesAnAccountDeactivatedSinceLogin() throws Exception {
        UserDetails stale = sessionSnapshot();

        // Pendant que la session vit encore : UserAdminService.setUserActive(..., false).
        User administered = userRepository.findById(userId).orElseThrow();
        administered.setActive(false);
        userRepository.save(administered);

        String body = objectMapper.writeValueAsString(Map.of("firstName", "Amina Fatima", "lastName", "Idrissi"));
        mockMvc.perform(put("/api/v1/profile").with(user(stale)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Amina Fatima"));

        // Le geste demandé a bien eu lieu...
        assertThat(column("first_name", String.class)).isEqualTo("Amina Fatima");
        // ... et rien d'autre n'a bougé : un compte désactivé le reste (§6.1).
        assertThat(column("active", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("§5.1 - enregistrer son propre nom ne rétablit pas l'ancien service de rattachement, donc l'ancien périmètre")
    void savingOwnNameNeverRevertsTheDepartmentAnAdministratorChanged() throws Exception {
        UserDetails stale = sessionSnapshot();

        // UserAdminService.updateUser : le compte change de service, donc de périmètre.
        User administered = userRepository.findById(userId).orElseThrow();
        administered.setDepartment(otherService);
        userRepository.save(administered);

        String body = objectMapper.writeValueAsString(Map.of("firstName", "Amina", "lastName", "Idrissi-Alaoui"));
        mockMvc.perform(put("/api/v1/profile").with(user(stale)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        assertThat(column("last_name", String.class)).isEqualTo("Idrissi-Alaoui");
        assertThat(column("department_id", Long.class)).isEqualTo(otherService.getId());
    }

    @Test
    @DisplayName("ADR-13 - enregistrer son propre nom ne lève pas un verrou de connexion posé depuis la connexion")
    void savingOwnNameNeverClearsALoginLockout() throws Exception {
        UserDetails stale = sessionSnapshot();

        // LoginAttemptListener sur une autre session : le compte est verrouillé.
        User administered = userRepository.findById(userId).orElseThrow();
        administered.setFailedLoginAttempts(4);
        userRepository.save(administered);

        String body = objectMapper.writeValueAsString(Map.of("firstName", "Amina", "lastName", "Idrissi"));
        mockMvc.perform(put("/api/v1/profile").with(user(stale)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        assertThat(column("failed_login_attempts", Integer.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("§13 - une réinitialisation administrative invalide l'ancien mot de passe tout de suite, pas à la prochaine connexion")
    void anAdministrativeResetInvalidatesTheOldPasswordImmediately() throws Exception {
        UserDetails stale = sessionSnapshot();

        // UserAdminService.resetPassword : l'empreinte en base n'est plus celle du cliché.
        User administered = userRepository.findById(userId).orElseThrow();
        administered.setPasswordHash(passwordEncoder.encode("AdminSet456"));
        userRepository.save(administered);

        // L'ancien mot de passe ne vaut plus rien, même pour la session qui l'a connu.
        String withOldPassword = objectMapper.writeValueAsString(
                Map.of("currentPassword", "OldPass123", "newPassword", "ChosenPass789"));
        mockMvc.perform(post("/api/v1/profile/password").with(user(stale)).with(csrf())
                        .contentType("application/json").content(withOldPassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(passwordEncoder.matches("AdminSet456", column("password_hash", String.class))).isTrue();

        // Celui que l'administrateur vient de poser, lui, est accepté.
        String withResetPassword = objectMapper.writeValueAsString(
                Map.of("currentPassword", "AdminSet456", "newPassword", "ChosenPass789"));
        mockMvc.perform(post("/api/v1/profile/password").with(user(stale)).with(csrf())
                        .contentType("application/json").content(withResetPassword))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("ChosenPass789", column("password_hash", String.class))).isTrue();
    }
}
