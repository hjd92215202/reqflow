package com.reqflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.entity.*;
import com.reqflow.repository.*;
import com.reqflow.service.*;
import com.reqflow.util.JwtUtil;
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
class KnowledgeTimelineIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .target("1.0.9")
                .load()
                .migrate();
        try (var connection =
                        java.sql.DriverManager.getConnection(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword());
                var sql = connection.createStatement()) {
            sql.execute(
                    "INSERT INTO sys_user(id,username,password_hash)"
                        + " VALUES(9701,'knowledge-owner','unused'),(9702,'knowledge-stranger','unused'),(9703,'knowledge-member','unused')");
            sql.execute(
                    "INSERT INTO req_workspace(id,name,owner_id)"
                            + " VALUES(9701,'knowledge-workspace',9701)");
            sql.execute("INSERT INTO req_workspace_member(workspace_id,user_id) VALUES(9701,9703)");
            sql.execute(
                    "INSERT INTO req_project(id,workspace_id,name,identifier)"
                            + " VALUES(9701,9701,'knowledge-project','KNOWLEDGE')");
            sql.execute(
                    "INSERT INTO req_wiki_document(id,title,content,tags,creator_id,share_token)"
                            + " VALUES(9701,'legacy wiki','keep body','keep"
                            + " tags',9701,'keep-share-token')");
        }
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    @Autowired WikiDocumentService wiki;
    @Autowired WikiDocumentRepository wikis;
    @Autowired RequirementRepository requirements;
    @Autowired StageRepository stages;
    @Autowired SubTaskRepository tasks;
    @Autowired RequirementTimelineService timeline;
    @SpyBean ActivityLogService activity;

    String token(long user) {
        return "Bearer " + JwtUtil.generateToken(user, "knowledge-test");
    }

    Requirement requirement(Long project) {
        var r = new Requirement();
        r.setCreatorId(9701L);
        r.setTitle("requirement");
        r.setProjectId(project);
        return requirements.saveAndFlush(r);
    }

    WikiDocument draft(Long requirement) {
        var d = new WikiDocument();
        d.setRequirementId(requirement);
        d.setTitle("审阅后的文档");
        d.setContent("人工修改的内容\n[来源](/requirements/" + requirement + ")");
        d.setTags("keep");
        d.setDocumentType("EXPERIMENT");
        d.setClientRequestId(UUID.randomUUID().toString());
        return d;
    }

    WikiDocument copy(WikiDocument d) throws Exception {
        return mapper.readValue(mapper.writeValueAsBytes(d), WikiDocument.class);
    }

    @Test
    void migrationAndOldClientUpdatesPreserveTypeBodyTagsAndSharing() throws Exception {
        var legacy = wiki.getWikiDocumentById(9701L, 9701L);
        assertThat(legacy.getDocumentType()).isNull();
        assertThat(legacy.getContent()).isEqualTo("keep body");
        assertThat(legacy.getTags()).isEqualTo("keep tags");
        assertThat(legacy.getShareToken()).isEqualTo("keep-share-token");
        mvc.perform(get("/api/wikis/share/keep-share-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("keep body"));
        var r = requirement(null);
        var saved = wiki.createWikiDocument(draft(r.getId()), 9701L);
        var old =
                mapper.readValue(
                        "{\"title\":\"old client\",\"content\":\"keep revised\",\"tags\":\"old"
                                + " tags\",\"requirementId\":"
                                + r.getId()
                                + "}",
                        WikiDocument.class);
        assertThat(wiki.updateWikiDocument(saved.getId(), old, 9701L).getDocumentType())
                .isEqualTo("EXPERIMENT");
        old.setDocumentType(null);
        assertThat(wiki.updateWikiDocument(saved.getId(), old, 9701L).getDocumentType()).isNull();
    }

    @Test
    void permissionsCoverReadListParentsRelinkingMutationAndShare() throws Exception {
        var r = requirement(9701L);
        var linked = wiki.createWikiDocument(draft(r.getId()), 9701L);
        var privateDoc = wiki.createWikiDocument(draft(null), 9701L);
        mvc.perform(get("/api/wikis/" + linked.getId())).andExpect(status().isUnauthorized());
        for (long id : List.of(linked.getId(), privateDoc.getId())) {
            mvc.perform(get("/api/wikis/" + id).header("Authorization", token(9702)))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/api/wikis/" + id + "/share-token")
                                    .header("Authorization", token(9702)))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/wikis/" + id).header("Authorization", token(9702)))
                    .andExpect(status().isForbidden());
        }
        assertThat(wiki.getWikiDocuments(null, 9702L)).isEmpty();
        assertThat(wiki.getWikiDocuments(null, 9703L))
                .extracting(WikiDocument::getId)
                .contains(linked.getId())
                .doesNotContain(privateDoc.getId());
        mvc.perform(
                        get("/api/wikis")
                                .param("requirementId", r.getId().toString())
                                .header("Authorization", token(9702)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/wikis/" + linked.getId()).header("Authorization", token(9703)))
                .andExpect(status().isOk());
        var invalid = draft(null);
        invalid.setParentId(privateDoc.getId());
        assertThatThrownBy(() -> wiki.createWikiDocument(invalid, 9702L))
                .isInstanceOf(ResponseStatusException.class);
        invalid.setParentId(null);
        invalid.setRequirementId(r.getId());
        assertThatThrownBy(() -> wiki.createWikiDocument(invalid, 9702L))
                .isInstanceOf(ResponseStatusException.class);
        var own = wiki.createWikiDocument(draft(null), 9702L);
        var rebind = copy(own);
        rebind.setRequirementId(r.getId());
        assertThatThrownBy(() -> wiki.updateWikiDocument(own.getId(), rebind, 9702L))
                .isInstanceOf(ResponseStatusException.class);
        var cycle = copy(linked);
        cycle.setParentId(linked.getId());
        assertThatThrownBy(() -> wiki.updateWikiDocument(linked.getId(), cycle, 9701L))
                .isInstanceOf(ResponseStatusException.class);
        String share = wiki.getOrCreateShareToken(linked.getId(), 9701L);
        assertThat(wiki.getWikiDocumentByShareToken(share).getContent())
                .isEqualTo(linked.getContent());
    }

    @Test
    void saveIdentityValidationConcurrentRetryAndAuditAreAtomic() throws Exception {
        var r = requirement(null);
        var input = draft(r.getId());
        input.setId(9701L);
        input.setCreatorId(9702L);
        input.setShareToken("forged");
        var pool = Executors.newFixedThreadPool(2);
        WikiDocument saved;
        try {
            var a = copy(input);
            var b = copy(input);
            var first = pool.submit(() -> wiki.createWikiDocument(a, 9701L));
            var second = pool.submit(() -> wiki.createWikiDocument(b, 9701L));
            saved = first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS).getId()).isEqualTo(saved.getId());
        } finally {
            pool.shutdownNow();
        }
        assertThat(saved.getId()).isNotEqualTo(9701L);
        assertThat(saved.getCreatorId()).isEqualTo(9701L);
        assertThat(saved.getShareToken()).isNull();
        assertThat(timeline.list(r.getId(), 9701L, "KNOWLEDGE", 0, 20, null).totalElements())
                .isEqualTo(1);
        var changed = copy(input);
        changed.setContent("changed");
        assertThatThrownBy(() -> wiki.createWikiDocument(changed, 9701L))
                .isInstanceOf(ResponseStatusException.class);
        var invalid = draft(r.getId());
        invalid.setDocumentType("UNKNOWN");
        mvc.perform(
                        post("/api/wikis")
                                .header("Authorization", token(9701))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest());
        long before = wikis.count();
        doThrow(new IllegalStateException("audit unavailable"))
                .when(activity)
                .record(eq(r.getId()), anyLong(), eq("WIKI"), anyLong(), anyString(), anyString());
        try {
            assertThatThrownBy(() -> wiki.createWikiDocument(draft(r.getId()), 9701L))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(wikis.count()).isEqualTo(before);
            var update = copy(saved);
            update.setContent("must rollback");
            assertThatThrownBy(() -> wiki.updateWikiDocument(saved.getId(), update, 9701L))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(wikis.findById(saved.getId()).orElseThrow().getContent())
                    .isEqualTo(saved.getContent());
            assertThatThrownBy(() -> wiki.deleteWikiDocument(saved.getId(), 9701L))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(wikis.existsById(saved.getId())).isTrue();
        } finally {
            reset(activity);
        }
    }

    @Test
    void timelineFiltersAllEventsAndPinsPagesAgainstNewInsertsWithContext() {
        var r = requirement(null);
        var s = new Stage();
        s.setRequirementId(r.getId());
        s.setTitle("stage");
        stages.saveAndFlush(s);
        var t = new SubTask();
        t.setStageId(s.getId());
        t.setTitle("task");
        t.setStatus("DONE");
        tasks.saveAndFlush(t);
        for (int i = 0; i < 26; i++)
            activity.record(r.getId(), 9701L, "SUB_TASK", t.getId(), "TASK_UPDATE", "event " + i);
        activity.record(
                r.getId(), 9701L, "DECISION", 987654L, "DECISION_CREATE", "deleted decision");
        activity.record(
                r.getId(),
                9701L,
                "VERIFICATION",
                987654L,
                "VERIFICATION_CREATE",
                "deleted verification");
        wiki.createWikiDocument(draft(r.getId()), 9701L);
        sql.update(
                "UPDATE req_activity_log SET created_at='2026-01-01T00:00:00' WHERE"
                        + " requirement_id=?",
                r.getId());
        var first = timeline.list(r.getId(), 9701L, "ALL", 0, 20, null);
        assertThat(first.totalElements()).isEqualTo(29);
        activity.record(r.getId(), 9701L, "SUB_TASK", t.getId(), "TASK_UPDATE", "new event");
        var second = timeline.list(r.getId(), 9701L, "ALL", 1, 20, first.snapshotId());
        assertThat(second.totalElements()).isEqualTo(29);
        var ids = new HashSet<Long>();
        first.content().forEach(i -> assertThat(ids.add(i.event().getId())).isTrue());
        second.content().forEach(i -> assertThat(ids.add(i.event().getId())).isTrue());
        assertThat(ids).hasSize(29);
        assertThat(
                        timeline.list(r.getId(), 9701L, "OPERATION", 0, 100, first.snapshotId())
                                .totalElements())
                .isEqualTo(26);
        for (String category : List.of("DECISION", "VERIFICATION", "KNOWLEDGE"))
            assertThat(timeline.list(r.getId(), 9701L, category, 0, 20, null).totalElements())
                    .isEqualTo(1);
        var taskItem = second.content().get(0);
        assertThat(taskItem.stageTitle()).isEqualTo("stage");
        assertThat(taskItem.subTaskTitle()).isEqualTo("task");
        assertThat(taskItem.currentStatus()).isEqualTo("DONE");
        assertThat(taskItem.sourceAvailable()).isTrue();
        assertThat(
                        first.content().stream()
                                .filter(i -> "DECISION".equals(i.event().getTargetType()))
                                .findFirst()
                                .orElseThrow()
                                .sourceAvailable())
                .isFalse();
        assertThatThrownBy(() -> timeline.list(r.getId(), 9702L, "ALL", 0, 20, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> timeline.list(r.getId(), 9701L, "INVALID", 0, 20, null))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(timeline.list(r.getId(), 9701L, "ALL", -1, 1000, null).size()).isEqualTo(100);
    }

    @Test
    void knowledgeHistorySurvivesUnlinkAndDeleteWithoutLeakingReboundDocument() throws Exception {
        var a = requirement(null);
        var b = requirement(null);
        var saved = wiki.createWikiDocument(draft(a.getId()), 9701L);
        var move = copy(saved);
        move.setRequirementId(b.getId());
        wiki.updateWikiDocument(saved.getId(), move, 9701L);
        var old = timeline.list(a.getId(), 9701L, "KNOWLEDGE", 0, 20, null);
        assertThat(old.totalElements()).isEqualTo(2);
        assertThat(old.content()).allMatch(i -> !i.sourceAvailable());
        assertThat(timeline.list(b.getId(), 9701L, "KNOWLEDGE", 0, 20, null).content())
                .allMatch(RequirementTimelineService.Item::sourceAvailable);
        wiki.deleteWikiDocument(saved.getId(), 9701L);
        var deleted = timeline.list(b.getId(), 9701L, "KNOWLEDGE", 0, 20, null);
        assertThat(deleted.totalElements()).isEqualTo(2);
        assertThat(deleted.content()).allMatch(i -> !i.sourceAvailable());
    }
}
