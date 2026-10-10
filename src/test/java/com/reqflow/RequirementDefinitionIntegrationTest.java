package com.reqflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.reqflow.dto.RequirementDefinition;
import com.reqflow.entity.Requirement;
import com.reqflow.repository.RequirementRepository;
import com.reqflow.service.RequirementDefinitionService;
import com.reqflow.util.JwtUtil;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.baseline-on-migrate=false"
        })
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RequirementDefinitionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("1.0.5")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO sys_user (id, username, password_hash) VALUES (9001,"
                            + " 'definition-test-owner', 'unused')");
            statement.execute(
                    "INSERT INTO req_requirement (id, title, description, creator_id) VALUES (9001,"
                            + " 'legacy', 'must survive', 9001)");
        }
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired RequirementDefinitionService definitions;
    @Autowired RequirementRepository requirements;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;

    @Test
    void authenticatedApiUsesRealPermissionChecksAndMatchesTheFrontendContract() throws Exception {
        var requirement = create();
        String ownerToken = "Bearer " + JwtUtil.generateToken(9001L, "definition-test-owner");
        String strangerToken = "Bearer " + JwtUtil.generateToken(9999L, "stranger");
        String path = "/api/requirements/" + requirement.getId() + "/definition";
        mvc.perform(get("/api/capabilities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/capabilities").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementDefinition").value(1));
        mvc.perform(get(path).header("Authorization", strangerToken))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put(path)
                                .header("Authorization", strangerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0,\"definition\":{}}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put(path)
                                .header("Authorization", ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"version\":0,\"definition\":{\"problemStatement\":\"API"
                                                + " problem\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(path).header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definition.problemStatement").value("API problem"));
    }

    private Requirement create() {
        Requirement requirement = new Requirement();
        requirement.setTitle("integration");
        requirement.setCreatorId(9001L);
        return requirements.saveAndFlush(requirement);
    }

    private RequirementDefinition content(String problem) {
        return new RequirementDefinition(
                problem,
                "目标",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new RequirementDefinition.SuccessCriterion(
                                "criterion-1", "成功标准", "测试", "")));
    }

    @Test
    void upgradePreservesLegacyDataAndJsonRoundTripsWithPersonalAudit() throws Exception {
        var legacy = requirements.findById(9001L).orElseThrow();
        assertThat(legacy.getDescription()).isEqualTo("must survive");
        assertThat(definitions.get(9001L, 9001L).state()).isEqualTo("NOT_STARTED");
        var requirement = create();
        definitions.save(requirement.getId(), 9001L, 0L, content("问题"));
        definitions.confirm(requirement.getId(), 9001L, 1L);
        var result = definitions.get(requirement.getId(), 9001L);
        assertThat(result.state()).isEqualTo("CONFIRMED");
        assertThat(result.definition().successCriteria().getFirst().id()).isEqualTo("criterion-1");
        try (var connection =
                        DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM req_activity_log WHERE requirement_id = ? AND"
                                        + " workspace_id IS NULL")) {
            statement.setLong(1, requirement.getId());
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void concurrentWritersCannotBothSaveTheSameVersion() throws Exception {
        var requirement = create();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptSave(requirement.getId(), start, "first"));
            var second = executor.submit(() -> attemptSave(requirement.getId(), start, "second"));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved", "conflict");
        }
        assertThat(definitions.get(requirement.getId(), 9001L).version()).isEqualTo(1);
    }

    private String attemptSave(Long id, CountDownLatch start, String problem) throws Exception {
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
        try {
            definitions.save(id, 9001L, 0L, content(problem));
            return "saved";
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.CONFLICT) throw exception;
            return "conflict";
        }
    }

    @Test
    void staleLegacyEntitySaveDoesNotOverwriteNewDefinition() throws Exception {
        var requirement = create();
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch definitionSaved = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var staleSave =
                    executor.submit(
                            () ->
                                    new TransactionTemplate(transactions)
                                            .executeWithoutResult(
                                                    status -> {
                                                        Requirement stale =
                                                                requirements
                                                                        .findById(
                                                                                requirement.getId())
                                                                        .orElseThrow();
                                                        loaded.countDown();
                                                        try {
                                                            if (!definitionSaved.await(
                                                                    10, TimeUnit.SECONDS))
                                                                throw new IllegalStateException(
                                                                        "timeout");
                                                        } catch (InterruptedException exception) {
                                                            Thread.currentThread().interrupt();
                                                            throw new IllegalStateException(
                                                                    exception);
                                                        }
                                                        stale.setTitle("legacy edited title");
                                                        requirements.saveAndFlush(stale);
                                                    }));
            assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            definitions.save(requirement.getId(), 9001L, 0L, content("new definition"));
            definitionSaved.countDown();
            staleSave.get(20, TimeUnit.SECONDS);
        }
        assertThat(requirements.findById(requirement.getId()).orElseThrow().getTitle())
                .isEqualTo("legacy edited title");
        var saved = definitions.get(requirement.getId(), 9001L);
        assertThat(saved.definition().problemStatement()).isEqualTo("new definition");
        assertThat(saved.version()).isEqualTo(1);
    }
}
