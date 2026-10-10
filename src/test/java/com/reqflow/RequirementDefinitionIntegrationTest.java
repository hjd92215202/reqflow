package com.reqflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.dto.RequirementDefinition;
import com.reqflow.dto.StageUpdateRequest;
import com.reqflow.dto.SubTaskUpdateRequest;
import com.reqflow.entity.Requirement;
import com.reqflow.repository.RequirementRepository;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.service.RequirementDefinitionService;
import com.reqflow.service.StageService;
import com.reqflow.service.SubTaskService;
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
            statement.execute(
                    "INSERT INTO req_stage (id, requirement_id, title) VALUES (9001, 9001, 'legacy"
                            + " stage')");
            statement.execute(
                    "INSERT INTO req_sub_task (id, stage_id, title, note, custom_fields) VALUES"
                            + " (9001, 9001, 'legacy task', 'keep note', '{\"link\":\"legacy\"}')");
        }
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired RequirementDefinitionService definitions;
    @Autowired RequirementRepository requirements;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StageRepository stages;
    @Autowired SubTaskRepository tasks;
    @Autowired StageService stageService;
    @Autowired SubTaskService taskService;

    private String ownerToken() {
        return "Bearer " + JwtUtil.generateToken(9001L, "definition-test-owner");
    }

    private long postEntity(String path, String json) throws Exception {
        var response =
                mvc.perform(
                                post(path)
                                        .header("Authorization", ownerToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readTree(response).get("id").asLong();
    }

    @Test
    void stageAndTaskStandardsRoundTripWithoutChangingLegacyFields() throws Exception {
        long requirementId = create().getId();
        long stageId =
                postEntity(
                        "/api/stages",
                        "{\"requirementId\":"
                                + requirementId
                                + ",\"title\":\"stage\",\"startDate\":\"2026-10-11\",\"goal\":\" "
                                + " goal "
                                + " \",\"expectedOutput\":\"output\",\"exitCriteria\":\"exit\"}");
        mvc.perform(
                        put("/api/stages/" + stageId)
                                .header("Authorization", ownerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value("goal"))
                .andExpect(jsonPath("$.expectedOutput").value("output"))
                .andExpect(jsonPath("$.exitCriteria").value("exit"))
                .andExpect(jsonPath("$.startDate").value("2026-10-11"));
        long taskId =
                postEntity(
                        "/api/subtasks",
                        "{\"stageId\":"
                                + stageId
                                + ",\"title\":\"task\",\"assignee\":\"definition-test-owner\",\"note\":\"note\",\"customFields\":{\"link\":\"keep\"},\"deliverable\":\""
                                + " artifact \",\"completionCriteria\":\"tests pass\"}");
        long childId =
                postEntity(
                        "/api/subtasks",
                        "{\"stageId\":"
                                + stageId
                                + ",\"parentId\":"
                                + taskId
                                + ",\"title\":\"quick child\"}");
        mvc.perform(
                        put("/api/subtasks/" + taskId)
                                .header("Authorization", ownerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"IN_PROGRESS\",\"endDate\":\"2026-10-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverable").value("artifact"))
                .andExpect(jsonPath("$.completionCriteria").value("tests pass"))
                .andExpect(jsonPath("$.note").value("note"))
                .andExpect(jsonPath("$.customFields.link").value("keep"));
        mvc.perform(
                        patch("/api/todos/" + taskId + "/toggle?isProjectTask=true")
                                .header("Authorization", ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(
                        put("/api/todos/" + taskId)
                                .header("Authorization", ownerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"isProjectTask\":true,\"title\":\"edited from"
                                            + " todo\",\"status\":\"DONE\",\"dueDate\":\"2026-10-13\"}"))
                .andExpect(status().isOk());
        var saved = tasks.findById(taskId).orElseThrow();
        assertThat(saved.getDeliverable()).isEqualTo("artifact");
        assertThat(saved.getCompletionCriteria()).isEqualTo("tests pass");
        assertThat(saved.getNote()).isEqualTo("note");
        assertThat(saved.getCustomFields()).containsEntry("link", "keep");
        assertThat(tasks.findById(childId).orElseThrow().getParentId()).isEqualTo(taskId);
        mvc.perform(
                        put("/api/subtasks/" + taskId)
                                .header("Authorization", ownerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"deliverable\":null,\"completionCriteria\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverable").doesNotExist())
                .andExpect(jsonPath("$.completionCriteria").doesNotExist())
                .andExpect(jsonPath("$.title").value("edited from todo"))
                .andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(
                        put("/api/stages/" + stageId)
                                .header("Authorization", ownerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"goal\":\"\",\"expectedOutput\":null,\"exitCriteria\":\""
                                                + " \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").doesNotExist())
                .andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(
                        put("/api/subtasks/" + childId)
                                .header("Authorization", ownerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/stages/requirement/" + requirementId)
                                .header("Authorization", ownerToken()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/subtasks/stage/" + stageId).header("Authorization", ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void standardsValidatePermissionsBoundsAndLegacyMigration() throws Exception {
        var legacyStage = stages.findById(9001L).orElseThrow();
        assertThat(legacyStage.getGoal()).isNull();
        assertThat(legacyStage.getExpectedOutput()).isNull();
        assertThat(legacyStage.getExitCriteria()).isNull();
        var legacyTask = tasks.findById(9001L).orElseThrow();
        assertThat(legacyTask.getDeliverable()).isNull();
        assertThat(legacyTask.getCompletionCriteria()).isNull();
        assertThat(legacyTask.getNote()).isEqualTo("keep note");
        assertThat(legacyTask.getCustomFields()).containsEntry("link", "legacy");
        String stranger = "Bearer " + JwtUtil.generateToken(9999L, "stranger");
        for (String path : List.of("/api/stages/9001", "/api/subtasks/9001")) {
            mvc.perform(
                            put(path)
                                    .header("Authorization", stranger)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"goal\":\"no\",\"deliverable\":\"no\"}"))
                    .andExpect(status().isForbidden());
        }
        for (String field : List.of("goal", "expectedOutput", "exitCriteria")) {
            mvc.perform(
                            put("/api/stages/9001")
                                    .header("Authorization", ownerToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            mapper.writeValueAsString(
                                                    java.util.Map.of(field, "x".repeat(10001)))))
                    .andExpect(status().isBadRequest());
        }
        for (String field : List.of("deliverable", "completionCriteria")) {
            mvc.perform(
                            put("/api/subtasks/9001")
                                    .header("Authorization", ownerToken())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            mapper.writeValueAsString(
                                                    java.util.Map.of(field, "x".repeat(10001)))))
                    .andExpect(status().isBadRequest());
        }
        long otherStage =
                postEntity(
                        "/api/stages",
                        "{\"requirementId\":" + create().getId() + ",\"title\":\"other\"}");
        mvc.perform(
                        post("/api/subtasks")
                                .header("Authorization", ownerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"stageId\":"
                                                + otherStage
                                                + ",\"parentId\":9001,\"title\":\"wrong parent\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/capabilities").header("Authorization", ownerToken()))
                .andExpect(jsonPath("$.executionStandards").value(1));
    }

    @Test
    void staleStageAndTaskUpdatesDoNotOverwriteNewStandards() throws Exception {
        long stageId =
                postEntity(
                        "/api/stages",
                        "{\"requirementId\":" + create().getId() + ",\"title\":\"stage\"}");
        long taskId =
                postEntity("/api/subtasks", "{\"stageId\":" + stageId + ",\"title\":\"task\"}");
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch standardsSaved = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var staleSave =
                    executor.submit(
                            () ->
                                    new TransactionTemplate(transactions)
                                            .executeWithoutResult(
                                                    status -> {
                                                        var staleStage =
                                                                stages.findById(stageId)
                                                                        .orElseThrow();
                                                        var staleTask =
                                                                tasks.findById(taskId)
                                                                        .orElseThrow();
                                                        loaded.countDown();
                                                        try {
                                                            if (!standardsSaved.await(
                                                                    10, TimeUnit.SECONDS))
                                                                throw new IllegalStateException(
                                                                        "timeout");
                                                        } catch (InterruptedException exception) {
                                                            Thread.currentThread().interrupt();
                                                            throw new IllegalStateException(
                                                                    exception);
                                                        }
                                                        staleStage.setTitle("new title");
                                                        staleTask.setStatus("DONE");
                                                        stages.saveAndFlush(staleStage);
                                                        tasks.saveAndFlush(staleTask);
                                                    }));
            assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            stageService.updateStage(
                    stageId,
                    mapper.readValue(
                            "{\"goal\":\"new goal\",\"expectedOutput\":\"new"
                                    + " output\",\"exitCriteria\":\"new exit\"}",
                            StageUpdateRequest.class),
                    9001L);
            taskService.updateSubTask(
                    taskId,
                    mapper.readValue(
                            "{\"deliverable\":\"new artifact\",\"completionCriteria\":\"new"
                                    + " criteria\"}",
                            SubTaskUpdateRequest.class),
                    9001L);
            standardsSaved.countDown();
            staleSave.get(20, TimeUnit.SECONDS);
        }
        var stage = stages.findById(stageId).orElseThrow();
        assertThat(stage.getTitle()).isEqualTo("new title");
        assertThat(stage.getGoal()).isEqualTo("new goal");
        assertThat(stage.getExpectedOutput()).isEqualTo("new output");
        assertThat(stage.getExitCriteria()).isEqualTo("new exit");
        var task = tasks.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo("DONE");
        assertThat(task.getDeliverable()).isEqualTo("new artifact");
        assertThat(task.getCompletionCriteria()).isEqualTo("new criteria");
    }

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

    @Test
    void clientMetadataCannotOverwriteExistingRowsOrFieldPresence() throws Exception {
        long stageId =
                postEntity(
                        "/api/stages",
                        "{\"id\":9001,\"requirementId\":"
                                + create().getId()
                                + ",\"title\":\"new stage\",\"goal\":\"new goal\"}");
        assertThat(stageId).isNotEqualTo(9001);
        long taskId =
                postEntity(
                        "/api/subtasks",
                        "{\"id\":9001,\"stageId\":"
                                + stageId
                                + ",\"title\":\"new task\",\"deliverable\":\"new artifact\"}");
        assertThat(taskId).isNotEqualTo(9001);
        assertThat(stages.findById(9001L).orElseThrow().getTitle()).isEqualTo("legacy stage");
        assertThat(tasks.findById(9001L).orElseThrow().getTitle()).isEqualTo("legacy task");
        var omitted =
                mapper.readValue("{\"deliverableProvided\":true}", SubTaskUpdateRequest.class);
        assertThat(omitted.isDeliverableProvided()).isFalse();
        var supplied =
                mapper.readValue(
                        "{\"deliverable\":null,\"deliverableProvided\":false}",
                        SubTaskUpdateRequest.class);
        assertThat(supplied.isDeliverableProvided()).isTrue();
        var stageUpdate =
                mapper.readValue(
                        "{\"goal\":null,\"goalProvided\":false}", StageUpdateRequest.class);
        assertThat(stageUpdate.isGoalProvided()).isTrue();
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
