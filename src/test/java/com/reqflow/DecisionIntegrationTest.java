package com.reqflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.dto.*;
import com.reqflow.entity.Requirement;
import com.reqflow.entity.Stage;
import com.reqflow.entity.SubTask;
import com.reqflow.repository.*;
import com.reqflow.service.*;
import com.reqflow.util.JwtUtil;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
class DecisionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("1.0.7")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var sql = connection.createStatement()) {
            sql.execute(
                    "INSERT INTO sys_user (id,username,password_hash) VALUES"
                        + " (9901,'decision-owner','unused'),(9902,'decision-stranger','unused'),(9903,'decision-member','unused')");
            sql.execute(
                    "INSERT INTO req_requirement"
                        + " (id,title,description,creator_id,definition_json,definition_version)"
                        + " VALUES (9901,'legacy','keep"
                        + " description',9901,'{\"problemStatement\":\"keep definition\"}',3)");
            sql.execute(
                    "INSERT INTO req_stage (id,requirement_id,title,goal) VALUES (9901,9901,'legacy"
                            + " stage','keep goal')");
            sql.execute(
                    "INSERT INTO req_sub_task"
                        + " (id,stage_id,title,note,deliverable,completion_criteria,custom_fields)"
                        + " VALUES (9901,9901,'legacy task','keep note','keep artifact','keep"
                        + " criteria','{\"link\":\"keep link\"}')");
            sql.execute(
                    "INSERT INTO req_workspace (id,name,owner_id) VALUES (9901,'decision"
                            + " workspace',9901)");
            sql.execute(
                    "INSERT INTO req_project (id,workspace_id,name,identifier) VALUES"
                            + " (9901,9901,'decision project','DECISION')");
            sql.execute(
                    "INSERT INTO req_workspace_member (workspace_id,user_id) VALUES (9901,9903)");
        }
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DecisionService decisions;
    @Autowired DecisionRecordRepository repository;
    @Autowired RequirementRepository requirements;
    @Autowired StageRepository stages;
    @Autowired SubTaskRepository tasks;
    @Autowired ActivityLogRepository logs;
    @Autowired SubTaskService taskService;
    @Autowired StageService stageService;
    @Autowired RequirementService requirementService;
    @SpyBean ActivityLogService activity;

    record Fixture(long requirementId, long stageId, long taskId) {}

    private Fixture fixture() {
        var requirement = new Requirement();
        requirement.setTitle("requirement");
        requirement.setCreatorId(9901L);
        requirements.saveAndFlush(requirement);
        var stage = new Stage();
        stage.setTitle("stage context");
        stage.setRequirementId(requirement.getId());
        stages.saveAndFlush(stage);
        var task = new SubTask();
        task.setTitle("task context");
        task.setStageId(stage.getId());
        tasks.saveAndFlush(task);
        return new Fixture(requirement.getId(), stage.getId(), task.getId());
    }

    private String token(long user) {
        return "Bearer " + JwtUtil.generateToken(user, "decision-test");
    }

    private String path(long requirement) {
        return "/api/requirements/" + requirement + "/decisions";
    }

    private DecisionContent content(String title, String status) {
        return new DecisionContent(
                title,
                "why",
                List.of(new DecisionContent.Option("one option", "pros", "cons")),
                "ACCEPTED".equals(status) ? "option one" : null,
                "PROPOSED".equals(status) ? null : "trade-off",
                List.of("risk"),
                "MEDIUM",
                status,
                LocalDate.of(2026, 10, 20),
                new DecisionContent.AiAssistance(
                        List.of("COMPARISON"),
                        "compare approaches",
                        "human choice",
                        "MODIFIED",
                        "review manually"));
    }

    private DecisionResponse create(
            Fixture fixture, Long stage, Long task, String title, String status) throws Exception {
        var result =
                mvc.perform(
                                post(path(fixture.requirementId()))
                                        .header("Authorization", token(9901))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsString(
                                                        new DecisionCreateRequest(
                                                                stage,
                                                                task,
                                                                content(title, status)))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readValue(result, DecisionResponse.class);
    }

    private long auditCount(long requirement) {
        return logs.findByRequirementIdOrderByCreatedAtDescIdDesc(
                        requirement, PageRequest.of(0, 100))
                .getTotalElements();
    }

    @Test
    void createsAtAllThreeLevelsAndRevisionsUseServerIdentityAndVersion() throws Exception {
        var fixture = fixture();
        var root = create(fixture, null, null, "proposal", "PROPOSED");
        var stage = create(fixture, fixture.stageId(), null, "stage proposal", "PROPOSED");
        var task = create(fixture, null, fixture.taskId(), "task proposal", "PROPOSED");
        assertThat(root.stageId()).isNull();
        assertThat(stage.stageTitle()).isEqualTo("stage context");
        assertThat(task.stageId()).isEqualTo(fixture.stageId());
        assertThat(task.subTaskTitle()).isEqualTo("task context");
        assertThat(task.content().options()).hasSize(1);
        assertThat(task.content().chosenOption()).isNull();
        assertThat(task.content().aiAssistance().humanJudgment()).isEqualTo("human choice");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("version", 0);
        payload.put("content", content("adopted", "ACCEPTED"));
        payload.put("createdBy", 9902);
        payload.put("updatedBy", 9902);
        payload.put("stageId", 9901);
        mvc.perform(
                        put(path(fixture.requirementId()) + "/" + task.id())
                                .header("Authorization", token(9901))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.createdBy").value(9901))
                .andExpect(jsonPath("$.updatedBy").value(9901))
                .andExpect(jsonPath("$.stageId").value(fixture.stageId()))
                .andExpect(jsonPath("$.content.reviewDate").value("2026-10-20"));
        long before = auditCount(fixture.requirementId());
        mvc.perform(
                        put(path(fixture.requirementId()) + "/" + task.id())
                                .header("Authorization", token(9901))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                new DecisionWriteRequest(
                                                        1L, content("adopted", "ACCEPTED")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(auditCount(fixture.requirementId())).isEqualTo(before);
        mvc.perform(
                        put(path(fixture.requirementId()) + "/" + task.id())
                                .header("Authorization", token(9901))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                new DecisionWriteRequest(
                                                        0L, content("stale", "REJECTED")))))
                .andExpect(status().isConflict());
        mvc.perform(
                        put(path(fixture.requirementId()) + "/" + task.id())
                                .header("Authorization", token(9901))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                new DecisionWriteRequest(
                                                        1L, content("rejected", "REJECTED")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.content.status").value("REJECTED"));
        assertThat(
                        logs.findByRequirementIdOrderByCreatedAtDescIdDesc(
                                        fixture.requirementId(), PageRequest.of(0, 100))
                                .getContent())
                .extracting(log -> log.getActionType())
                .contains("DECISION_CREATE", "DECISION_ACCEPT", "DECISION_REJECT");
        mvc.perform(
                        get(path(fixture.requirementId()) + "/" + task.id())
                                .header("Authorization", token(9901)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.aiAssistance.handling").value("MODIFIED"));
    }

    @Test
    void validatesContentPermissionsAndCrossRequirementReferences() throws Exception {
        var fixture = fixture();
        var other = fixture();
        var original = create(fixture, null, null, "private", "PROPOSED");
        mvc.perform(get(path(fixture.requirementId()))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(fixture.requirementId())).header("Authorization", token(9902)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get(path(fixture.requirementId()) + "/" + original.id())
                                .header("Authorization", token(9902)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get(path(other.requirementId()) + "/" + original.id())
                                .header("Authorization", token(9901)))
                .andExpect(status().isForbidden());
        for (String method : List.of("update", "supersede")) {
            var body =
                    mapper.writeValueAsString(
                            new DecisionWriteRequest(0L, content("not yours", "ACCEPTED")));
            var request =
                    method.equals("update")
                            ? put(path(fixture.requirementId()) + "/" + original.id())
                            : post(
                                    path(fixture.requirementId())
                                            + "/"
                                            + original.id()
                                            + "/supersede");
            mvc.perform(
                            request.header("Authorization", token(9902))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isForbidden());
        }
        for (var body :
                List.of(
                        new DecisionCreateRequest(
                                other.stageId(), null, content("cross stage", "PROPOSED")),
                        new DecisionCreateRequest(
                                null, other.taskId(), content("cross task", "PROPOSED")),
                        new DecisionCreateRequest(
                                fixture.stageId(),
                                other.taskId(),
                                content("mismatch", "PROPOSED")))) {
            mvc.perform(
                            post(path(fixture.requirementId()))
                                    .header("Authorization", token(9901))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        List<Map<String, Object>> invalid =
                List.of(
                        Map.of("title", " "),
                        Map.of("context", ""),
                        Map.of("status", "ACCEPTED"),
                        Map.of("status", "REJECTED"),
                        Map.of("status", "SUPERSEDED"),
                        Map.of("confidence", "IMPOSSIBLE"),
                        Map.of("context", "x".repeat(10001)),
                        Map.of("options", List.of(Map.of("name", ""))),
                        Map.of("options", Collections.nCopies(21, Map.of("name", "option"))),
                        Map.of("assumptions", List.of(" ")),
                        Map.of("aiAssistance", Map.of("phases", List.of("CODING", "CODING"))),
                        Map.of("aiAssistance", Map.of("handling", "AUTO_VERIFIED")),
                        Map.of("reviewDate", "invalid"));
        long before = auditCount(fixture.requirementId());
        for (var fields : invalid) {
            var content =
                    mapper.convertValue(
                            content("bad", "PROPOSED"),
                            new TypeReference<Map<String, Object>>() {});
            content.putAll(fields);
            mvc.perform(
                            post(path(fixture.requirementId()))
                                    .header("Authorization", token(9901))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of("content", content))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(
                        decisions
                                .list(fixture.requirementId(), 9901L, null, null, null, 0, 20)
                                .getTotalElements())
                .isEqualTo(1);
        assertThat(auditCount(fixture.requirementId())).isEqualTo(before);
        mvc.perform(get("/api/capabilities").header("Authorization", token(9901)))
                .andExpect(jsonPath("$.decisionRecords").value(1));
    }

    @Test
    void pagesAndFiltersUseStableCreatedAtAndIdOrdering() throws Exception {
        var fixture = fixture();
        var first = create(fixture, null, null, "first", "PROPOSED");
        var second = create(fixture, fixture.stageId(), null, "second", "ACCEPTED");
        var third = create(fixture, fixture.stageId(), fixture.taskId(), "third", "PROPOSED");
        try (var connection =
                        DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var sql =
                        connection.prepareStatement(
                                "UPDATE req_decision_record SET created_at='2026-10-11 00:00:00'"
                                        + " WHERE requirement_id=?")) {
            sql.setLong(1, fixture.requirementId());
            sql.executeUpdate();
        }
        var page0 = decisions.list(fixture.requirementId(), 9901L, null, null, null, 0, 1);
        var page1 = decisions.list(fixture.requirementId(), 9901L, null, null, null, 1, 1);
        assertThat(page0.getContent().getFirst().id()).isEqualTo(third.id());
        assertThat(page1.getContent().getFirst().id()).isEqualTo(second.id());
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(
                        decisions
                                .list(
                                        fixture.requirementId(),
                                        9901L,
                                        fixture.stageId(),
                                        null,
                                        null,
                                        0,
                                        20)
                                .getContent())
                .extracting(DecisionResponse::id)
                .containsExactly(third.id(), second.id());
        assertThat(
                        decisions
                                .list(
                                        fixture.requirementId(),
                                        9901L,
                                        null,
                                        fixture.taskId(),
                                        null,
                                        0,
                                        20)
                                .getContent())
                .extracting(DecisionResponse::id)
                .containsExactly(third.id());
        mvc.perform(
                        get(path(fixture.requirementId()) + "?status=ACCEPTED&page=0&size=1")
                                .header("Authorization", token(9901)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.id()));
        assertThat(first.id()).isLessThan(second.id());
    }

    @Test
    void onlyOneConcurrentReplacementCanWinAndBothRecordsRemain() throws Exception {
        var fixture = fixture();
        var original = create(fixture, fixture.stageId(), fixture.taskId(), "source", "ACCEPTED");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(
                            () -> replaceAttempt(fixture, original, start, "replacement A"));
            var second =
                    executor.submit(
                            () -> replaceAttempt(fixture, original, start, "replacement B"));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved", "conflict");
        }
        var source = decisions.get(fixture.requirementId(), original.id(), 9901L);
        assertThat(source.content().status()).isEqualTo("SUPERSEDED");
        assertThat(source.content().chosenOption()).isEqualTo(original.content().chosenOption());
        assertThat(source.version()).isEqualTo(1);
        assertThat(source.supersededByDecisionId()).isNotNull();
        var replacement =
                decisions.get(fixture.requirementId(), source.supersededByDecisionId(), 9901L);
        assertThat(replacement.supersedesDecisionId()).isEqualTo(source.id());
        assertThat(replacement.subTaskId()).isEqualTo(fixture.taskId());
        assertThat(
                        decisions
                                .list(fixture.requirementId(), 9901L, null, null, null, 0, 20)
                                .getTotalElements())
                .isEqualTo(2);
        assertThat(auditCount(fixture.requirementId())).isEqualTo(3);
        mvc.perform(
                        put(path(fixture.requirementId()) + "/" + source.id())
                                .header("Authorization", token(9901))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                new DecisionWriteRequest(
                                                        1L, content("overwrite", "ACCEPTED")))))
                .andExpect(status().isConflict());
    }

    private String replaceAttempt(
            Fixture fixture, DecisionResponse original, CountDownLatch start, String title)
            throws Exception {
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
        try {
            decisions.supersede(
                    fixture.requirementId(),
                    original.id(),
                    9901L,
                    new DecisionWriteRequest(0L, content(title, "ACCEPTED")));
            return "saved";
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.CONFLICT) throw exception;
            return "conflict";
        }
    }

    @Test
    void auditFailureRollsBackCreationAndReplacementAtomically() throws Exception {
        var fixture = fixture();
        try {
            doThrow(new IllegalStateException("audit unavailable"))
                    .when(activity)
                    .record(
                            eq(fixture.requirementId()),
                            eq(9901L),
                            eq("DECISION"),
                            anyLong(),
                            eq("DECISION_CREATE"),
                            anyString());
            assertThatThrownBy(
                            () ->
                                    decisions.create(
                                            fixture.requirementId(),
                                            9901L,
                                            new DecisionCreateRequest(
                                                    null, null, content("rollback", "PROPOSED"))))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            reset(activity);
        }
        assertThat(
                        decisions
                                .list(fixture.requirementId(), 9901L, null, null, null, 0, 20)
                                .getTotalElements())
                .isZero();
        assertThat(auditCount(fixture.requirementId())).isZero();
        var source = create(fixture, null, null, "source", "ACCEPTED");
        try {
            doThrow(new IllegalStateException("audit unavailable"))
                    .when(activity)
                    .record(
                            eq(fixture.requirementId()),
                            eq(9901L),
                            eq("DECISION"),
                            eq(source.id()),
                            eq("DECISION_SUPERSEDE"),
                            anyString());
            assertThatThrownBy(
                            () ->
                                    decisions.supersede(
                                            fixture.requirementId(),
                                            source.id(),
                                            9901L,
                                            new DecisionWriteRequest(
                                                    0L,
                                                    content("rollback replacement", "ACCEPTED"))))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            reset(activity);
        }
        var preserved = decisions.get(fixture.requirementId(), source.id(), 9901L);
        assertThat(preserved.content().status()).isEqualTo("ACCEPTED");
        assertThat(preserved.supersededByDecisionId()).isNull();
        assertThat(preserved.version()).isZero();
        assertThat(
                        decisions
                                .list(fixture.requirementId(), 9901L, null, null, null, 0, 20)
                                .getTotalElements())
                .isEqualTo(1);
        assertThat(auditCount(fixture.requirementId())).isEqualTo(1);
    }

    @Test
    void deletionKeepsContextSnapshotsAndRequirementDeletionCleansItsRecords() throws Exception {
        var fixture = fixture();
        var source = create(fixture, fixture.stageId(), fixture.taskId(), "source", "ACCEPTED");
        var replacement =
                decisions.supersede(
                        fixture.requirementId(),
                        source.id(),
                        9901L,
                        new DecisionWriteRequest(0L, content("replacement", "ACCEPTED")));
        taskService.deleteSubTask(fixture.taskId(), 9901L);
        var afterTask = decisions.get(fixture.requirementId(), source.id(), 9901L);
        assertThat(afterTask.subTaskId()).isNull();
        assertThat(afterTask.subTaskTitle()).isEqualTo("task context");
        assertThat(afterTask.stageId()).isEqualTo(fixture.stageId());
        stageService.deleteStage(fixture.stageId(), 9901L);
        var afterStage = decisions.get(fixture.requirementId(), replacement.id(), 9901L);
        assertThat(afterStage.stageId()).isNull();
        assertThat(afterStage.stageTitle()).isEqualTo("stage context");
        assertThat(afterStage.supersedesDecisionId()).isEqualTo(source.id());
        var updated =
                decisions.update(
                        fixture.requirementId(),
                        replacement.id(),
                        9901L,
                        new DecisionWriteRequest(
                                0L, content("revised after deletion", "ACCEPTED")));
        assertThat(updated.stageId()).isNull();
        assertThat(updated.subTaskId()).isNull();
        requirementService.deleteRequirement(fixture.requirementId(), 9901L);
        assertThat(repository.findById(source.id())).isEmpty();
        assertThat(repository.findById(replacement.id())).isEmpty();
    }

    @Test
    void migrationPreservesPreviousDataAndProjectMembersInheritAccess() throws Exception {
        var legacy = requirements.findById(9901L).orElseThrow();
        assertThat(legacy.getDescription()).isEqualTo("keep description");
        assertThat(legacy.getDefinitionVersion()).isEqualTo(3);
        assertThat(legacy.getDefinition().problemStatement()).isEqualTo("keep definition");
        assertThat(stages.findById(9901L).orElseThrow().getGoal()).isEqualTo("keep goal");
        var task = tasks.findById(9901L).orElseThrow();
        assertThat(task.getDeliverable()).isEqualTo("keep artifact");
        assertThat(task.getNote()).isEqualTo("keep note");
        assertThat(task.getCustomFields()).containsEntry("link", "keep link");
        var fixture = fixture();
        var requirement = requirements.findById(fixture.requirementId()).orElseThrow();
        requirement.setProjectId(9901L);
        requirements.saveAndFlush(requirement);
        var original = create(fixture, null, null, "shared proposal", "PROPOSED");
        mvc.perform(
                        put(path(fixture.requirementId()) + "/" + original.id())
                                .header("Authorization", token(9903))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                new DecisionWriteRequest(
                                                        0L,
                                                        content("member revision", "ACCEPTED")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value(9901))
                .andExpect(jsonPath("$.updatedBy").value(9903));
        mvc.perform(get(path(fixture.requirementId())).header("Authorization", token(9903)))
                .andExpect(status().isOk());
        mvc.perform(get(path(fixture.requirementId())).header("Authorization", token(9902)))
                .andExpect(status().isForbidden());
    }
}
