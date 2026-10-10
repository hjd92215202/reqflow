package com.reqflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.dto.*;
import com.reqflow.entity.*;
import com.reqflow.repository.*;
import com.reqflow.service.*;
import com.reqflow.util.JwtUtil;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.baseline-on-migrate=false"
        })
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class VerificationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("1.0.8")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var sql = connection.createStatement()) {
            sql.execute(
                    "INSERT INTO sys_user(id,username,password_hash) VALUES"
                        + " (9801,'verification-owner','unused'),(9802,'verification-stranger','unused'),(9803,'verification-member','unused')");
            sql.execute(
                    "INSERT INTO req_workspace(id,name,owner_id)"
                            + " VALUES(9801,'verification-workspace',9801)");
            sql.execute(
                    "INSERT INTO req_project(id,workspace_id,name,identifier)"
                            + " VALUES(9801,9801,'verification-project','VERIFY')");
            sql.execute("INSERT INTO req_workspace_member(workspace_id,user_id) VALUES(9801,9803)");
            sql.execute(
                    "INSERT INTO"
                        + " req_requirement(id,title,description,creator_id,definition_json,definition_version)"
                        + " VALUES(9801,'legacy','keep"
                        + " description',9801,'{\"problemStatement\":\"legacy problem\"}',3)");
            sql.execute(
                    "INSERT INTO req_stage(id,requirement_id,title,goal) VALUES(9801,9801,'legacy"
                            + " stage','keep goal')");
            sql.execute(
                    "INSERT INTO"
                        + " req_sub_task(id,stage_id,title,note,deliverable,completion_criteria,custom_fields)"
                        + " VALUES(9801,9801,'legacy task','keep note','keep artifact','keep"
                        + " criteria','{\"link\":\"keep link\"}')");
            sql.execute(
                    "INSERT INTO"
                        + " req_decision_record(id,requirement_id,title,context,status,created_by,updated_by,created_at,updated_at)"
                        + " VALUES(9801,9801,'legacy decision','keep"
                        + " context','PROPOSED',9801,9801,now(),now())");
        }
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    @Autowired VerificationService service;
    @Autowired VerificationRecordRepository records;
    @Autowired RequirementRepository requirements;
    @Autowired StageRepository stages;
    @Autowired SubTaskRepository tasks;
    @Autowired RequirementDefinitionService definitions;
    @Autowired SubTaskService taskService;
    @Autowired StageService stageService;
    @Autowired RequirementService requirementService;
    @Autowired ActivityLogRepository logs;
    @SpyBean ActivityLogService activity;

    record Fixture(long requirement, long stage, long task) {}

    RequirementDefinition definition(String description) {
        return new RequirementDefinition(
                "问题",
                "目标",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new RequirementDefinition.SuccessCriterion(
                                "c1", description, "压测", "100ms")));
    }

    Fixture fixture() {
        var requirement = new Requirement();
        requirement.setCreatorId(9801L);
        requirement.setTitle("test");
        requirements.saveAndFlush(requirement);
        definitions.save(requirement.getId(), 9801L, 0L, definition("延迟达标"));
        var stage = new Stage();
        stage.setRequirementId(requirement.getId());
        stage.setTitle("阶段");
        stages.saveAndFlush(stage);
        var task = new SubTask();
        task.setStageId(stage.getId());
        task.setTitle("任务");
        task.setStatus("DONE");
        task.setDeliverable("代码");
        task.setCompletionCriteria("交付达标");
        tasks.saveAndFlush(task);
        return new Fixture(requirement.getId(), stage.getId(), task.getId());
    }

    VerificationCreateRequest request(Fixture f, String result, boolean criterion, boolean task) {
        var current = tasks.findById(f.task()).orElse(null);
        return new VerificationCreateRequest(
                UUID.randomUUID().toString(),
                task ? f.stage() : null,
                task ? f.task() : null,
                criterion ? "c1" : null,
                criterion
                        ? requirements
                                .findById(f.requirement())
                                .orElseThrow()
                                .getDefinitionVersion()
                        : null,
                task && current != null ? current.getDeliverable() : null,
                task && current != null ? current.getCompletionCriteria() : null,
                new VerificationContent(
                        criterion
                                ? requirements
                                        .findById(f.requirement())
                                        .orElseThrow()
                                        .getDefinition()
                                        .successCriteria()
                                        .get(0)
                                        .description()
                                : "任务交付",
                        "集成测试",
                        "预期",
                        "实际",
                        result,
                        "WAIVED".equals(result) ? "低风险，人工接受" : null,
                        List.of(
                                new VerificationContent.Evidence(
                                        "报告", "https://example.com/report", "测试记录")),
                        Instant.parse("2026-01-01T00:00:00Z")));
    }

    VerificationResponse create(Fixture f, String result, boolean criterion, boolean task) {
        return service.create(f.requirement(), 9801L, request(f, result, criterion, task));
    }

    String path(Fixture f) {
        return "/api/requirements/" + f.requirement() + "/verifications";
    }

    String token(long user) {
        return "Bearer " + JwtUtil.generateToken(user, "verification-test");
    }

    void saveDefinition(Fixture f, RequirementDefinition value) {
        definitions.save(
                f.requirement(),
                9801L,
                requirements.findById(f.requirement()).orElseThrow().getDefinitionVersion(),
                value);
    }

    long auditCount(Fixture f) {
        return logs
                .findByRequirementIdOrderByCreatedAtDescIdDesc(
                        f.requirement(), org.springframework.data.domain.PageRequest.of(0, 100))
                .stream()
                .filter(l -> "VERIFICATION".equals(l.getTargetType()))
                .count();
    }

    @Test
    void apiPermissionsValidationAndIdentity() throws Exception {
        var f = fixture();
        var body = request(f, "PASS", true, true);
        mvc.perform(get(path(f) + "/summary")).andExpect(status().isUnauthorized());
        for (String route : List.of(path(f), path(f) + "/summary"))
            mvc.perform(get(route).header("Authorization", token(9802)))
                    .andExpect(status().isForbidden());
        com.fasterxml.jackson.databind.node.ObjectNode json = mapper.valueToTree(body);
        json.put("verifiedBy", 9802);
        json.put("createdAt", "2000-01-01T00:00:00Z");
        var saved =
                mvc.perform(
                                post(path(f))
                                        .header("Authorization", token(9801))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.toString()))
                        .andExpect(status().isOk())
                        .andReturn();
        var response =
                mapper.readValue(
                        saved.getResponse().getContentAsByteArray(), VerificationResponse.class);
        assertThat(response.verifiedBy()).isEqualTo(9801);
        assertThat(response.subTaskTitle()).isEqualTo("任务");
        assertThat(response.createdAt()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
        mvc.perform(get(path(f) + "/" + response.id()).header("Authorization", token(9802)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(path(f) + "/" + response.id() + "/void")
                                .header("Authorization", token(9802))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
        for (String field :
                List.of("criterionSnapshot", "method", "actualResult", "resultStatus")) {
            var invalid = mapper.valueToTree(request(f, "PASS", true, true));
            invalid.withObject("/content").put(field, "");
            mvc.perform(
                            post(path(f))
                                    .header("Authorization", token(9801))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(invalid.toString()))
                    .andExpect(status().isBadRequest());
        }
        com.fasterxml.jackson.databind.node.ObjectNode invalid =
                mapper.valueToTree(request(f, "WAIVED", false, false));
        invalid.withObject("/content").putNull("waiverReason");
        mvc.perform(
                        post(path(f))
                                .header("Authorization", token(9801))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalid.toString()))
                .andExpect(status().isBadRequest());
        invalid = mapper.valueToTree(request(f, "PASS", false, false));
        invalid.withArray("/content/evidence")
                .get(0)
                .withObject("")
                .put("url", "javascript:alert(1)");
        mvc.perform(
                        post(path(f))
                                .header("Authorization", token(9801))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalid.toString()))
                .andExpect(status().isBadRequest());
        var other = fixture();
        invalid = mapper.valueToTree(request(f, "PASS", false, true));
        invalid.put("stageId", other.stage());
        mvc.perform(
                        post(path(f))
                                .header("Authorization", token(9801))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalid.toString()))
                .andExpect(status().isBadRequest());
        var req = requirements.findById(f.requirement()).orElseThrow();
        req.setProjectId(9801L);
        requirements.saveAndFlush(req);
        mvc.perform(get(path(f) + "/summary").header("Authorization", token(9803)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/capabilities").header("Authorization", token(9801)))
                .andExpect(jsonPath("$.verificationRecords").value(1));
        assertThat(auditCount(f)).isEqualTo(1);
        String longSnapshot = "😀标准".repeat(1000);
        com.fasterxml.jackson.databind.node.ObjectNode longBody =
                mapper.valueToTree(request(f, "PASS", false, false));
        longBody.withObject("/content").put("criterionSnapshot", longSnapshot);
        var longSaved =
                mvc.perform(
                                post(path(f))
                                        .header("Authorization", token(9801))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(longBody.toString()))
                        .andExpect(status().isOk())
                        .andReturn();
        var longRecord =
                mapper.readValue(
                        longSaved.getResponse().getContentAsByteArray(),
                        VerificationResponse.class);
        assertThat(longRecord.content().criterionSnapshot()).isEqualTo(longSnapshot);
        String auditSummary =
                sql.queryForObject(
                        "SELECT summary FROM req_activity_log WHERE target_type='VERIFICATION' AND"
                                + " target_id=?",
                        String.class,
                        longRecord.id());
        assertThat(auditSummary).contains("…").doesNotContain("\uFFFD");
        assertThat(auditSummary.codePointCount(0, auditSummary.length())).isLessThanOrEqualTo(1000);
    }

    @Test
    void doneIsNotPassAndLatestUsesRecordingOrderWithSeparateTaskAndCriterionCounts() {
        var f = fixture();
        var summary = service.summary(f.requirement(), 9801L);
        assertThat(summary.doneUnverified()).isEqualTo(1);
        assertThat(summary.distribution().get("UNVERIFIED")).isEqualTo(1);
        create(f, "PASS", false, true);
        summary = service.summary(f.requirement(), 9801L);
        assertThat(summary.doneUnverified()).isZero();
        assertThat(summary.distribution().get("UNVERIFIED")).isEqualTo(1);
        var pass = create(f, "PASS", true, true);
        var fail = create(f, "FAIL", true, true);
        sql.update(
                "UPDATE req_verification_record SET created_at='2026-10-11T00:00:00Z' WHERE id IN"
                        + " (?,?)",
                pass.id(),
                fail.id());
        sql.update(
                "UPDATE req_verification_record SET verified_at='2000-01-01T00:00:00Z' WHERE id=?",
                fail.id());
        summary = service.summary(f.requirement(), 9801L);
        assertThat(summary.criteria().get(0).latest().id()).isEqualTo(fail.id());
        assertThat(summary.distribution().get("FAIL")).isEqualTo(1);
        assertThat(
                        service.list(f.requirement(), 9801L, null, null, null, null, null, 0, 20)
                                .getTotalElements())
                .isEqualTo(3);
        // Restore actual server chronology before creating later records.
        sql.update(
                "UPDATE req_verification_record SET created_at='2020-01-01T00:00:00Z' WHERE"
                        + " requirement_id=?",
                f.requirement());
        for (String result : List.of("PARTIAL", "INCONCLUSIVE", "WAIVED")) {
            create(f, result, true, false);
            summary = service.summary(f.requirement(), 9801L);
            assertThat(summary.distribution().get(result)).isEqualTo(1);
            assertThat(summary.distribution().get("PASS")).isZero();
        }
        assertThat(tasks.findById(f.task()).orElseThrow().getStatus()).isEqualTo("DONE");
    }

    @Test
    void changedDeletedAndReusedCriterionNeverResurrectsOldPass() {
        var f = fixture();
        var pass = create(f, "PASS", true, false);
        var stale = request(f, "PASS", true, false);
        var same = definition("延迟达标");
        saveDefinition(
                f,
                new RequirementDefinition(
                        "新背景", "目标", List.of(), List.of(), List.of(), same.successCriteria()));
        assertThat(service.get(f.requirement(), pass.id(), 9801L).valid()).isTrue();
        saveDefinition(f, definition("更严格延迟"));
        assertThatThrownBy(() -> service.create(f.requirement(), 9801L, stale))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThat(service.get(f.requirement(), pass.id(), 9801L).content().criterionSnapshot())
                .isEqualTo("延迟达标");
        assertThat(service.get(f.requirement(), pass.id(), 9801L).valid()).isFalse();
        saveDefinition(f, same);
        assertThat(service.summary(f.requirement(), 9801L).distribution().get("UNVERIFIED"))
                .isEqualTo(1);
        var fresh = create(f, "PASS", true, false);
        saveDefinition(
                f,
                new RequirementDefinition("问题", "目标", List.of(), List.of(), List.of(), List.of()));
        saveDefinition(f, same);
        assertThat(service.get(f.requirement(), fresh.id(), 9801L).valid()).isFalse();
        assertThat(service.summary(f.requirement(), 9801L).distribution().get("UNVERIFIED"))
                .isEqualTo(1);
    }

    @Test
    void taskStandardsAndContextDeletionInvalidateButPreserveSnapshots() {
        var f = fixture();
        var pass = create(f, "PASS", true, true);
        sql.update("UPDATE req_sub_task SET note='新备注',status='IN_PROGRESS' WHERE id=?", f.task());
        assertThat(service.get(f.requirement(), pass.id(), 9801L).valid()).isTrue();
        sql.update("UPDATE req_sub_task SET completion_criteria='新标准' WHERE id=?", f.task());
        assertThat(service.get(f.requirement(), pass.id(), 9801L).valid()).isFalse();
        sql.update("UPDATE req_sub_task SET completion_criteria='交付达标' WHERE id=?", f.task());
        assertThat(service.summary(f.requirement(), 9801L).criteria().get(0).latest()).isNull();
        var fail = create(f, "FAIL", false, true);
        taskService.deleteSubTask(f.task(), 9801L);
        var historical = service.get(f.requirement(), fail.id(), 9801L);
        assertThat(historical.subTaskId()).isNull();
        assertThat(historical.subTaskTitle()).isEqualTo("任务");
        assertThat(historical.taskCompletionCriteria()).isEqualTo("交付达标");
        assertThat(historical.valid()).isFalse();
        stageService.deleteStage(f.stage(), 9801L);
        historical = service.get(f.requirement(), fail.id(), 9801L);
        assertThat(historical.stageId()).isNull();
        assertThat(historical.stageTitle()).isEqualTo("阶段");
        requirementService.deleteRequirement(f.requirement(), 9801L);
        assertThat(records.findById(fail.id())).isEmpty();
    }

    @Test
    void voidAndAllAuditFailuresRollbackBusinessChanges() {
        var f = fixture();
        var pass = create(f, "PASS", true, false);
        var fail = create(f, "FAIL", true, false);
        assertThatThrownBy(() -> service.voidRecord(f.requirement(), fail.id(), 9801L, " "))
                .isInstanceOf(ResponseStatusException.class);
        try {
            doThrow(new IllegalStateException("audit failed"))
                    .when(activity)
                    .record(
                            eq(f.requirement()),
                            anyLong(),
                            eq("VERIFICATION"),
                            anyLong(),
                            anyString(),
                            anyString());
            assertThatThrownBy(
                            () ->
                                    service.create(
                                            f.requirement(),
                                            9801L,
                                            request(f, "PASS", true, false)))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.voidRecord(f.requirement(), fail.id(), 9801L, "纠正录入"))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(
                            () ->
                                    service.createRepair(
                                            f.requirement(),
                                            fail.id(),
                                            9801L,
                                            new VerificationService.RepairRequest(
                                                    UUID.randomUUID().toString(),
                                                    f.stage(),
                                                    "修复",
                                                    null,
                                                    null)))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            reset(activity);
        }
        assertThat(auditCount(f)).isEqualTo(2);
        assertThat(service.get(f.requirement(), fail.id(), 9801L).voidedAt()).isNull();
        assertThat(tasks.findByStageIdOrderByIdAsc(f.stage())).hasSize(1);
        var voided = service.voidRecord(f.requirement(), fail.id(), 9801L, "重复录入纠正");
        assertThat(voided.valid()).isFalse();
        assertThat(voided.content().resultStatus()).isEqualTo("FAIL");
        assertThat(service.summary(f.requirement(), 9801L).criteria().get(0).latest().id())
                .isEqualTo(pass.id());
        assertThat(
                        service.list(f.requirement(), 9801L, null, null, null, null, false, 0, 20)
                                .getContent())
                .hasSize(1);
        try {
            doThrow(new IllegalStateException("audit failed"))
                    .when(activity)
                    .record(
                            eq(f.requirement()),
                            anyLong(),
                            eq("REQUIREMENT"),
                            anyLong(),
                            eq("DEFINITION_UPDATE"),
                            anyString());
            assertThatThrownBy(() -> saveDefinition(f, definition("changed")))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            reset(activity);
        }
        assertThat(service.get(f.requirement(), pass.id(), 9801L).valid()).isTrue();
    }

    @Test
    void repairRetainsSourceIsIdempotentAndReverificationCanPass() throws Exception {
        var f = fixture();
        var fail = create(f, "FAIL", true, true);
        var body =
                new VerificationService.RepairRequest(
                        UUID.randomUUID().toString(), f.stage(), "修复任务", "修复代码", "重复压测");
        var repair = service.createRepair(f.requirement(), fail.id(), 9801L, body);
        assertThat(repair.getRepairVerificationId()).isEqualTo(fail.id());
        assertThat(repair.getStatus()).isEqualTo("TODO");
        assertThat(repair.getNote()).contains("验证 #" + fail.id());
        assertThat(service.createRepair(f.requirement(), fail.id(), 9801L, body).getId())
                .isEqualTo(repair.getId());
        var other = fixture();
        assertThatThrownBy(
                        () ->
                                service.createRepair(
                                        f.requirement(),
                                        fail.id(),
                                        9801L,
                                        new VerificationService.RepairRequest(
                                                UUID.randomUUID().toString(),
                                                other.stage(),
                                                "bad",
                                                null,
                                                null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(service.summary(f.requirement(), 9801L).distribution().get("FAIL")).isEqualTo(1);
        create(f, "PASS", true, true);
        assertThat(service.summary(f.requirement(), 9801L).distribution().get("PASS")).isEqualTo(1);
        assertThat(service.get(f.requirement(), fail.id(), 9801L).content().resultStatus())
                .isEqualTo("FAIL");
        mvc.perform(
                        post("/api/subtasks")
                                .header("Authorization", token(9801))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"stageId\":"
                                                + f.stage()
                                                + ",\"title\":\"ordinary\",\"repairVerificationId\":"
                                                + fail.id()
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repairVerificationId").isEmpty());
    }

    @Test
    void concurrentRetryCreatesOneRecordAndStablePagesDoNotRepeat() throws Exception {
        var f = fixture();
        var body = request(f, "PASS", true, true);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                return service.create(f.requirement(), 9801L, body);
                            });
            var second =
                    pool.submit(
                            () -> {
                                start.await();
                                return service.create(f.requirement(), 9801L, body);
                            });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).id())
                    .isEqualTo(second.get(15, TimeUnit.SECONDS).id());
        } finally {
            pool.shutdownNow();
        }
        assertThat(auditCount(f)).isEqualTo(1);
        var second = create(f, "FAIL", true, true);
        var third = create(f, "WAIVED", false, false);
        sql.update(
                "UPDATE req_verification_record SET created_at='2026-10-11T00:00:00Z' WHERE"
                        + " requirement_id=?",
                f.requirement());
        assertThat(
                        service.list(f.requirement(), 9801L, null, null, null, null, null, 0, 1)
                                .getContent()
                                .get(0)
                                .id())
                .isEqualTo(third.id());
        assertThat(
                        service.list(f.requirement(), 9801L, null, null, null, null, null, 1, 1)
                                .getContent()
                                .get(0)
                                .id())
                .isEqualTo(second.id());
        assertThat(
                        service.list(
                                        f.requirement(),
                                        9801L,
                                        f.stage(),
                                        f.task(),
                                        "c1",
                                        "FAIL",
                                        true,
                                        0,
                                        20)
                                .getContent())
                .hasSize(1);
        assertThat(service.summary(f.requirement(), 9801L).criteria().get(0).latest().id())
                .isEqualTo(second.id());
        var changed =
                new VerificationCreateRequest(
                        body.clientRequestId(),
                        body.stageId(),
                        body.subTaskId(),
                        body.successCriterionId(),
                        body.definitionVersion(),
                        body.taskDeliverable(),
                        body.taskCompletionCriteria(),
                        new VerificationContent(
                                "延迟达标", "方法", null, "不同", "PASS", null, List.of(), null));
        assertThatThrownBy(() -> service.create(f.requirement(), 9801L, changed))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void upgradeFromM3PreservesDefinitionStandardsDecisionAndNotes() {
        var requirement = requirements.findById(9801L).orElseThrow();
        assertThat(requirement.getDescription()).isEqualTo("keep description");
        assertThat(requirement.getDefinitionVersion()).isEqualTo(3);
        var task = tasks.findById(9801L).orElseThrow();
        assertThat(task.getNote()).isEqualTo("keep note");
        assertThat(task.getDeliverable()).isEqualTo("keep artifact");
        assertThat(task.getCustomFields()).containsEntry("link", "keep link");
        assertThat(task.getRepairVerificationId()).isNull();
        assertThat(stages.findById(9801L).orElseThrow().getGoal()).isEqualTo("keep goal");
        assertThat(
                        sql.queryForObject(
                                "SELECT context FROM req_decision_record WHERE id=9801",
                                String.class))
                .isEqualTo("keep context");
        assertThat(service.summary(9801L, 9801L).criteria()).isEmpty();
    }
}
