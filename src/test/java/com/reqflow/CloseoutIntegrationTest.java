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
import java.time.*;
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
class CloseoutIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("1.0.10")
                .load()
                .migrate();
        try (var c =
                        java.sql.DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var s = c.createStatement()) {
            s.execute(
                    "INSERT INTO sys_user(id,username,password_hash)"
                        + " VALUES(9601,'closeout-owner','unused'),(9602,'closeout-stranger','unused'),(9603,'closeout-member','unused')");
            s.execute(
                    "INSERT INTO req_workspace(id,name,owner_id)"
                            + " VALUES(9601,'closeout-workspace',9601)");
            s.execute(
                    "INSERT INTO req_project(id,workspace_id,name,identifier)"
                            + " VALUES(9601,9601,'closeout-project','CLOSEOUT')");
            s.execute("INSERT INTO req_workspace_member(workspace_id,user_id) VALUES(9601,9603)");
            s.execute(
                    "INSERT INTO"
                        + " req_requirement(id,title,description,status,creator_id,definition_version)"
                        + " VALUES(9601,'legacy','keep description','IN_PROGRESS',9601,3)");
            s.execute(
                    "INSERT INTO"
                        + " req_wiki_document(id,requirement_id,title,content,tags,creator_id,share_token,document_type)"
                        + " VALUES(9601,9601,'legacy wiki','keep body','keep"
                        + " tags',9601,'closeout-share','EXPERIMENT')");
        }
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired CloseoutService service;
    @Autowired RequirementRepository requirements;
    @Autowired StageRepository stages;
    @Autowired SubTaskRepository tasks;
    @Autowired RequirementDefinitionService definitions;
    @Autowired VerificationService verification;
    @Autowired DecisionService decisions;
    @Autowired WikiDocumentService wiki;
    @Autowired RequirementCloseoutRepository closeouts;
    @Autowired RequirementService requirementService;
    @Autowired RequirementTimelineService timeline;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    @SpyBean ActivityLogService activity;

    record Fixture(long req, long stage, long task) {}

    RequirementDefinition definition(String goal) {
        return new RequirementDefinition(
                "问题",
                goal,
                List.of(),
                List.of(),
                List.of(),
                List.of(new RequirementDefinition.SuccessCriterion("c1", "延迟达标", "压测", "100ms")));
    }

    Fixture fixture() {
        var r = new Requirement();
        r.setTitle("closeout");
        r.setStatus("IN_PROGRESS");
        r.setCreatorId(9601L);
        r.setProjectId(9601L);
        requirements.saveAndFlush(r);
        definitions.save(r.getId(), 9601L, 0L, definition("目标"));
        definitions.confirm(r.getId(), 9601L, 1L);
        var s = new Stage();
        s.setTitle("stage");
        s.setRequirementId(r.getId());
        stages.saveAndFlush(s);
        var t = new SubTask();
        t.setTitle("task");
        t.setStageId(s.getId());
        t.setStatus("DONE");
        tasks.saveAndFlush(t);
        return new Fixture(r.getId(), s.getId(), t.getId());
    }

    VerificationResponse verify(Fixture f, String result) {
        return verification.create(
                f.req(),
                9601L,
                new VerificationCreateRequest(
                        UUID.randomUUID().toString(),
                        f.stage(),
                        f.task(),
                        "c1",
                        requirements.findById(f.req()).orElseThrow().getDefinitionVersion(),
                        null,
                        null,
                        new VerificationContent(
                                "延迟达标",
                                "压测",
                                "100ms",
                                "实际失败",
                                result,
                                "WAIVED".equals(result) ? "人工接受" : null,
                                List.of(
                                        new VerificationContent.Evidence(
                                                "报告", "https://example.com", "证据")),
                                null)));
    }

    CloseoutContent handled(CloseoutResponse response) {
        return new CloseoutContent(
                "NOT_ACHIEVED",
                "用户确认未达目标，接受风险并安排后续修复",
                response.facts().issues().stream()
                        .map(
                                i ->
                                        new CloseoutContent.Disposition(
                                                i.key(), "ACCEPT_RISK", "原因与下一步行动"))
                        .toList(),
                "后续修复",
                "用于整理",
                "人工核对",
                null);
    }

    CloseoutService.Save input(CloseoutResponse response, CloseoutContent c) {
        return new CloseoutService.Save(response.record().version(), response.factsToken(), c);
    }

    CloseoutResponse complete(Fixture f) {
        var current = service.get(f.req(), 9601L);
        return service.save(f.req(), 9601L, input(current, handled(current)), true);
    }

    String path(Fixture f) {
        return "/api/requirements/" + f.req() + "/closeout";
    }

    String token(long user) {
        return "Bearer " + JwtUtil.generateToken(user, "closeout-test");
    }

    @Test
    void migrationReadOnlyGenerationPermissionsAndSavedWikiValidation() throws Exception {
        var legacy = service.get(9601L, 9601L);
        assertThat(legacy.record().status()).isEqualTo("NOT_STARTED");
        assertThat(closeouts.existsById(9601L)).isFalse();
        assertThat(
                        sql.queryForObject(
                                "SELECT content FROM req_wiki_document WHERE id=9601",
                                String.class))
                .isEqualTo("keep body");
        assertThat(
                        sql.queryForObject(
                                "SELECT document_type FROM req_wiki_document WHERE id=9601",
                                String.class))
                .isEqualTo("EXPERIMENT");
        var f = fixture();
        mvc.perform(get(path(f))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(f)).header("Authorization", token(9602)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(f)).header("Authorization", token(9603))).andExpect(status().isOk());
        var response = service.get(f.req(), 9601L);
        var content = handled(response);
        for (String suffix : List.of("", "/complete"))
            mvc.perform(
                            (suffix.isEmpty() ? put(path(f)) : post(path(f) + suffix))
                                    .header("Authorization", token(9602))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsBytes(input(response, content))))
                    .andExpect(status().isForbidden());
        var foreign =
                new CloseoutContent(
                        content.outcome(),
                        content.conclusion(),
                        content.dispositions(),
                        null,
                        null,
                        null,
                        9601L);
        assertThatThrownBy(() -> service.save(f.req(), 9601L, input(response, foreign), false))
                .isInstanceOf(ResponseStatusException.class);
        var d = new WikiDocument();
        d.setRequirementId(f.req());
        d.setTitle("reviewed wiki");
        d.setContent("human edited");
        d = wiki.createWikiDocument(d, 9601L);
        var linked =
                new CloseoutContent(
                        content.outcome(),
                        content.conclusion(),
                        content.dispositions(),
                        null,
                        null,
                        null,
                        d.getId());
        var saved = service.save(f.req(), 9603L, input(response, linked), false);
        assertThat(saved.record().content().wikiDocumentId()).isEqualTo(d.getId());
        assertThat(saved.record().status()).isEqualTo("DRAFT");
        assertThat(saved.record().completedAt()).isNull();
        assertThatThrownBy(() -> service.reopen(f.req(), 9603L, saved.record().version() - 1))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(
                        sql.queryForObject(
                                "SELECT share_token FROM req_wiki_document WHERE id=?",
                                String.class,
                                d.getId()))
                .isNull();
    }

    @Test
    void unresolvedFactsNeedHandlingButFailureCanCompleteWithoutChangingRequirement()
            throws Exception {
        var f = fixture();
        verify(f, "WAIVED");
        var content =
                new DecisionContent(
                        "pending decision",
                        "context",
                        List.of(),
                        null,
                        null,
                        List.of(),
                        null,
                        "PROPOSED",
                        null,
                        null);
        decisions.create(f.req(), 9601L, new DecisionCreateRequest(null, null, content));
        var current = service.get(f.req(), 9601L);
        assertThat(current.facts().issues())
                .extracting(CloseoutResponse.Issue::status)
                .contains("WAIVED", "PROPOSED");
        var missing = new CloseoutContent("PARTIAL", "总结", List.of(), null, null, null, null);
        assertThatThrownBy(() -> service.save(f.req(), 9601L, input(current, missing), true))
                .isInstanceOf(ResponseStatusException.class);
        var draft = service.save(f.req(), 9601L, input(current, missing), false);
        assertThat(draft.record().status()).isEqualTo("DRAFT");
        var body = mapper.valueToTree(input(draft, handled(draft)));
        body.withObject("").put("completedBy", 9602).put("status", "DONE");
        mvc.perform(
                        post(path(f) + "/complete")
                                .header("Authorization", token(9601))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record.completedBy").value(9601));
        assertThat(requirements.findById(f.req()).orElseThrow().getStatus())
                .isEqualTo("IN_PROGRESS");
        var saved = service.get(f.req(), 9601L);
        assertThat(saved.record().status()).isEqualTo("COMPLETE");
        assertThat(saved.needsReview()).isFalse();
        assertThat(saved.facts().verification().distribution().get("WAIVED")).isEqualTo(1);
        assertThat(timeline.list(f.req(), 9601L, "OPERATION", 0, 100, null).content())
                .anyMatch(
                        i ->
                                "CLOSEOUT_COMPLETE".equals(i.event().getActionType())
                                        && i.sourceAvailable());
    }

    @Test
    void staleFactsAndRecordVersionsRejectAndCompletedChangesNeverRestoreOldConfirmation() {
        var f = fixture();
        verify(f, "PASS");
        var old = service.get(f.req(), 9601L);
        verify(f, "FAIL");
        assertThatThrownBy(() -> service.save(f.req(), 9601L, input(old, handled(old)), true))
                .isInstanceOf(ResponseStatusException.class);
        var current = service.get(f.req(), 9601L);
        var done = service.save(f.req(), 9601L, input(current, handled(current)), true);
        definitions.save(
                f.req(),
                9601L,
                requirements.findById(f.req()).orElseThrow().getDefinitionVersion(),
                definition("新目标"));
        assertThat(service.get(f.req(), 9601L).needsReview()).isTrue();
        definitions.save(
                f.req(),
                9601L,
                requirements.findById(f.req()).orElseThrow().getDefinitionVersion(),
                definition("目标"));
        assertThat(service.get(f.req(), 9601L).needsReview()).isTrue();
        assertThatThrownBy(() -> service.save(f.req(), 9601L, input(done, handled(done)), false))
                .isInstanceOf(ResponseStatusException.class);
        var opened = service.reopen(f.req(), 9601L, done.record().version());
        assertThat(opened.record().status()).isEqualTo("DRAFT");
        assertThat(opened.record().content().conclusion()).contains("未达目标");
        assertThat(service.reopen(f.req(), 9601L, done.record().version()).record().version())
                .isEqualTo(opened.record().version());
        assertThat(
                        sql.queryForObject(
                                "SELECT count(*) FROM req_activity_log WHERE requirement_id=? AND"
                                        + " action_type='CLOSEOUT_REOPEN'",
                                Long.class,
                                f.req()))
                .isEqualTo(1);
        assertThatThrownBy(() -> service.save(f.req(), 9601L, input(done, handled(done)), true))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(service.save(f.req(), 9601L, input(opened, handled(opened)), true).needsReview())
                .isFalse();
    }

    @Test
    void taskDecisionVerificationAndLinkedWikiChangesRequireReview() {
        var f = fixture();
        var passed = verify(f, "PASS");
        var done = complete(f);
        var t = tasks.findById(f.task()).orElseThrow();
        t.setStatus("TODO");
        tasks.saveAndFlush(t);
        assertThat(service.get(f.req(), 9601L).needsReview()).isTrue();
        t.setStatus("DONE");
        tasks.saveAndFlush(t);
        assertThat(service.get(f.req(), 9601L).needsReview()).isTrue();
        var opened = service.reopen(f.req(), 9601L, done.record().version());
        done = service.save(f.req(), 9601L, input(opened, handled(opened)), true);
        verification.voidRecord(f.req(), passed.id(), 9601L, "重新核验");
        assertThat(service.get(f.req(), 9601L).needsReview()).isTrue();
        var decision =
                new DecisionContent(
                        "复查",
                        "context",
                        List.of(new DecisionContent.Option("A", null, null)),
                        "A",
                        "理由",
                        List.of(),
                        null,
                        "ACCEPTED",
                        LocalDate.now(ZoneOffset.UTC),
                        null);
        decisions.create(f.req(), 9601L, new DecisionCreateRequest(null, null, decision));
        assertThat(service.get(f.req(), 9601L).facts().issues())
                .anyMatch(i -> "REVIEW_DUE".equals(i.status()));
        var d = new WikiDocument();
        d.setRequirementId(f.req());
        d.setTitle("复盘");
        d.setContent("审阅正文");
        d = wiki.createWikiDocument(d, 9601L);
        opened = service.reopen(f.req(), 9601L, done.record().version());
        var value = handled(opened);
        var linked =
                new CloseoutContent(
                        value.outcome(),
                        value.conclusion(),
                        value.dispositions(),
                        null,
                        null,
                        null,
                        d.getId());
        service.save(f.req(), 9601L, input(opened, linked), true);
        d.setContent("文档修改");
        wiki.updateWikiDocument(d.getId(), d, 9601L);
        assertThat(service.get(f.req(), 9601L).needsReview()).isTrue();
        wiki.deleteWikiDocument(d.getId(), 9601L);
        var after = service.get(f.req(), 9601L);
        assertThat(after.needsReview()).isTrue();
        assertThat(after.record().content().wikiDocumentId()).isNull();
    }

    @Test
    void identicalConcurrentCompletionIsIdempotentAndAuditFailureRollsBack() throws Exception {
        var f = fixture();
        var current = service.get(f.req(), 9601L);
        var input = input(current, handled(current));
        var pool = Executors.newFixedThreadPool(2);
        CloseoutResponse done;
        try {
            var a = pool.submit(() -> service.save(f.req(), 9601L, input, true));
            var b = pool.submit(() -> service.save(f.req(), 9601L, input, true));
            done = a.get(20, TimeUnit.SECONDS);
            assertThat(b.get(20, TimeUnit.SECONDS).record().version())
                    .isEqualTo(done.record().version());
        } finally {
            pool.shutdownNow();
        }
        assertThat(
                        sql.queryForObject(
                                "SELECT count(*) FROM req_activity_log WHERE requirement_id=? AND"
                                        + " action_type='CLOSEOUT_COMPLETE'",
                                Long.class,
                                f.req()))
                .isEqualTo(1);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(activity)
                .record(
                        eq(f.req()),
                        anyLong(),
                        eq("CLOSEOUT"),
                        anyLong(),
                        anyString(),
                        anyString());
        final var version = done.record().version();
        try {
            assertThatThrownBy(() -> service.reopen(f.req(), 9601L, version))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(service.get(f.req(), 9601L).record().status()).isEqualTo("COMPLETE");
        } finally {
            reset(activity);
        }
        var another = fixture();
        var first = service.get(another.req(), 9601L);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(activity)
                .record(
                        eq(another.req()),
                        anyLong(),
                        eq("CLOSEOUT"),
                        anyLong(),
                        anyString(),
                        anyString());
        try {
            assertThatThrownBy(
                            () ->
                                    service.save(
                                            another.req(),
                                            9601L,
                                            input(first, handled(first)),
                                            true))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(closeouts.existsById(another.req())).isFalse();
        } finally {
            reset(activity);
        }
    }

    @Test
    void requirementDeleteCleansCloseoutAndFactsRevisionRollbackMatchesBusiness() {
        var f = fixture();
        complete(f);
        var old = service.get(f.req(), 9601L);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(activity)
                .record(
                        eq(f.req()),
                        anyLong(),
                        eq("REQUIREMENT"),
                        anyLong(),
                        eq("DEFINITION_UPDATE"),
                        anyString());
        try {
            assertThatThrownBy(
                            () ->
                                    definitions.save(
                                            f.req(),
                                            9601L,
                                            requirements
                                                    .findById(f.req())
                                                    .orElseThrow()
                                                    .getDefinitionVersion(),
                                            definition("must rollback")))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            reset(activity);
        }
        assertThat(service.get(f.req(), 9601L).factsToken()).isEqualTo(old.factsToken());
        assertThat(service.get(f.req(), 9601L).needsReview()).isFalse();
        requirementService.deleteRequirement(f.req(), 9601L);
        assertThat(closeouts.existsById(f.req())).isFalse();
        assertThat(
                        sql.queryForObject(
                                "SELECT count(*) FROM req_closeout_facts WHERE requirement_id=?",
                                Long.class,
                                f.req()))
                .isZero();
    }
}
