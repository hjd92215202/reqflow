package com.reqflow;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.baseline-on-migrate=false"
        })
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class EngineeringGrowthHttpIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    JsonNode request(String method, String path, Object body, String token, int status)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as(method + " " + path + " -> " + response.body())
                .isEqualTo(status);
        return response.body().startsWith("{") || response.body().startsWith("[")
                ? mapper.readTree(response.body())
                : mapper.getNodeFactory().textNode(response.body());
    }

    String account() throws Exception {
        String username = "http-" + UUID.randomUUID();
        request(
                "POST",
                "/api/auth/register",
                Map.of("username", username, "passwordHash", "test-password", "nickname", "HTTP验收"),
                null,
                200);
        return request(
                        "POST",
                        "/api/auth/login",
                        Map.of("username", username, "password", "test-password"),
                        null,
                        200)
                .path("token")
                .asText();
    }

    JsonNode get(String path, String token) throws Exception {
        return request("GET", path, null, token, 200);
    }

    JsonNode post(String path, Object body, String token) throws Exception {
        return request("POST", path, body, token, 200);
    }

    JsonNode put(String path, Object body, String token) throws Exception {
        return request("PUT", path, body, token, 200);
    }

    ObjectNode decision(String title) {
        var c = mapper.createObjectNode();
        c.put("title", title)
                .put("context", "收尾需要原子校验事实")
                .put("chosenOption", "独立记录与单调修订号")
                .put("rationale", "保留失败事实并拒绝过期确认")
                .put("status", "ACCEPTED");
        c.putArray("options").addObject().put("name", "独立记录与单调修订号");
        c.putArray("assumptions").add("使用 PostgreSQL");
        var ai = c.putObject("aiAssistance");
        ai.putArray("phases").add("CODING");
        ai.put("contribution", "整理实现候选")
                .put("humanJudgment", "人工决定状态规则")
                .put("handling", "MODIFIED")
                .put("verification", "集成测试");
        return c;
    }

    ObjectNode verification(long stage, long task, long version, String result) {
        var b = mapper.createObjectNode();
        b.put("clientRequestId", UUID.randomUUID().toString())
                .put("stageId", stage)
                .put("subTaskId", task)
                .put("successCriterionId", "c1")
                .put("definitionVersion", version)
                .put("taskDeliverable", "收尾API和UI")
                .put("taskCompletionCriteria", "事实变化必须复核");
        b.putObject("content")
                .put("criterionSnapshot", "过期清单不能确认收尾")
                .put("method", "真实HTTP回归")
                .put("expectedResult", "返回409")
                .put("actualResult", result.equals("PASS") ? "返回409" : "发现边界问题")
                .put("resultStatus", result)
                .putArray("evidence")
                .addObject()
                .put("title", "测试记录")
                .put("description", "本机隔离数据库执行");
        return b;
    }

    ObjectNode closeout(JsonNode current, long wiki) {
        var b = mapper.createObjectNode();
        b.put("version", current.path("record").path("version").asLong())
                .put("factsToken", current.path("factsToken").asText());
        var c = b.putObject("content");
        c.put("outcome", "ACHIEVED")
                .put("conclusion", "主要目标通过，遗留事项人工处置")
                .put("nextActions", "真实用户试用收集反馈")
                .put("aiUse", "协助整理")
                .put("humanJudgment", "人工确认")
                .put("wikiDocumentId", wiki);
        var dispositions = c.putArray("dispositions");
        current.path("facts")
                .path("issues")
                .forEach(
                        i ->
                                dispositions
                                        .addObject()
                                        .put("key", i.path("key").asText())
                                        .put("handling", "KEEP_OPEN")
                                        .put("reason", "明确保留后续行动"));
        return b;
    }

    @Test
    void emptyDatabaseInitializesAndWholeWorkflowSurvivesHttpReloads() throws Exception {
        assertThat(
                        sql.queryForObject(
                                "SELECT version FROM flyway_schema_history WHERE success ORDER BY"
                                        + " installed_rank DESC LIMIT 1",
                                String.class))
                .isEqualTo("1.0.11");
        request("GET", "/api/capabilities", null, null, 401);
        String owner = account(), stranger = account();
        assertThat(get("/api/capabilities", owner).path("requirementCloseout").asInt())
                .isEqualTo(1);
        long req =
                post("/api/requirements", Map.of("title", "ReqFlow收尾与复盘HTTP验收"), owner)
                        .path("id")
                        .asLong();
        String root = "/api/requirements/" + req;
        assertThat(get(root + "/definition", owner).path("state").asText())
                .isEqualTo("NOT_STARTED");
        var def = mapper.createObjectNode();
        def.put("version", 0);
        var content = def.putObject("definition");
        content.put("problemStatement", "过期事实可能导致错误收尾").put("targetOutcome", "收尾可解释且不会覆盖历史");
        content.putArray("constraints").add("保留旧客户端");
        content.putArray("assumptions").add("有隔离数据库");
        content.putArray("outOfScope").add("公开作品");
        content.putArray("successCriteria")
                .addObject()
                .put("id", "c1")
                .put("description", "过期清单不能确认收尾")
                .put("suggestedMethod", "真实HTTP回归")
                .put("targetValue", "409");
        long version = put(root + "/definition", def, owner).path("version").asLong();
        post(root + "/definition/confirm", Map.of("version", version), owner);
        version = get(root + "/definition", owner).path("version").asLong();
        assertThat(get(root + "/definition", owner).path("definition")).isEqualTo(content);
        long stage =
                post(
                                "/api/stages",
                                Map.of(
                                        "requirementId",
                                        req,
                                        "title",
                                        "实施与验证",
                                        "goal",
                                        "闭环可走通",
                                        "expectedOutput",
                                        "API和界面",
                                        "exitCriteria",
                                        "回归通过"),
                                owner)
                        .path("id")
                        .asLong();
        long task =
                post(
                                "/api/subtasks",
                                Map.of(
                                        "stageId",
                                        stage,
                                        "title",
                                        "完成收尾",
                                        "deliverable",
                                        "收尾API和UI",
                                        "completionCriteria",
                                        "事实变化必须复核",
                                        "note",
                                        "保留执行说明"),
                                owner)
                        .path("id")
                        .asLong();
        var taskDone = put("/api/subtasks/" + task, Map.of("status", "DONE"), owner);
        assertThat(taskDone.path("deliverable").asText()).isEqualTo("收尾API和UI");
        assertThat(get(root + "/verifications/summary", owner).path("doneUnverified").asInt())
                .isEqualTo(1);
        var d =
                post(
                        root + "/decisions",
                        Map.of("stageId", stage, "subTaskId", task, "content", decision("独立收尾")),
                        owner);
        var replacement =
                post(
                        root + "/decisions/" + d.path("id").asLong() + "/supersede",
                        Map.of(
                                "version",
                                d.path("version").asLong(),
                                "content",
                                decision("使用单调事实修订号")),
                        owner);
        assertThat(
                        get(root + "/decisions/" + d.path("id").asLong(), owner)
                                .path("content")
                                .path("status")
                                .asText())
                .isEqualTo("SUPERSEDED");
        assertThat(replacement.path("supersedesDecisionId").asLong())
                .isEqualTo(d.path("id").asLong());
        var passed =
                post(root + "/verifications", verification(stage, task, version, "PASS"), owner);
        assertThat(
                        get(root + "/verifications/summary", owner)
                                .path("distribution")
                                .path("PASS")
                                .asInt())
                .isEqualTo(1);
        var failed =
                post(root + "/verifications", verification(stage, task, version, "FAIL"), owner);
        assertThat(
                        get(root + "/verifications/summary", owner)
                                .path("distribution")
                                .path("FAIL")
                                .asInt())
                .isEqualTo(1);
        var repair =
                post(
                        root + "/verifications/" + failed.path("id").asLong() + "/repair-tasks",
                        Map.of(
                                "clientRequestId",
                                UUID.randomUUID().toString(),
                                "stageId",
                                stage,
                                "title",
                                "修复并重验"),
                        owner);
        assertThat(repair.path("repairVerificationId").asLong())
                .isEqualTo(failed.path("id").asLong());
        put("/api/subtasks/" + repair.path("id").asLong(), Map.of("status", "DONE"), owner);
        var independent = mapper.createObjectNode();
        independent
                .put("clientRequestId", UUID.randomUUID().toString())
                .put("stageId", stage)
                .put("subTaskId", repair.path("id").asLong());
        independent
                .putObject("content")
                .put("criterionSnapshot", "修复交付")
                .put("method", "回归")
                .put("actualResult", "通过")
                .put("resultStatus", "PASS")
                .putArray("evidence");
        post(root + "/verifications", independent, owner);
        assertThat(
                        get(root + "/verifications/summary", owner)
                                .path("distribution")
                                .path("FAIL")
                                .asInt())
                .isEqualTo(1);
        post(root + "/verifications", verification(stage, task, version, "PASS"), owner);
        assertThat(
                        get(root + "/verifications/summary", owner)
                                .path("distribution")
                                .path("PASS")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        get(root + "/verifications/" + passed.path("id").asLong(), owner)
                                .path("content")
                                .path("resultStatus")
                                .asText())
                .isEqualTo("PASS");
        assertThat(
                        get(root + "/verifications/" + failed.path("id").asLong(), owner)
                                .path("content")
                                .path("resultStatus")
                                .asText())
                .isEqualTo("FAIL");
        var beforeWiki = get(root + "/closeout", owner);
        assertThat(beforeWiki.path("record").path("status").asText()).isEqualTo("NOT_STARTED");
        var wiki =
                post(
                        "/api/wikis",
                        Map.of(
                                "clientRequestId",
                                UUID.randomUUID().toString(),
                                "requirementId",
                                req,
                                "title",
                                "人工审阅复盘",
                                "content",
                                "人工补充适用条件与证据\n[需求](#/requirements/" + req + ")",
                                "documentType",
                                "RETROSPECTIVE"),
                        owner);
        long wikiId = wiki.path("id").asLong();
        assertThat(wiki.path("shareToken").isNull()).isTrue();
        var current = get(root + "/closeout", owner);
        var body = closeout(current, wikiId);
        put(root + "/closeout", body, owner);
        current = get(root + "/closeout", owner);
        body = closeout(current, wikiId);
        var done = post(root + "/closeout/complete", body, owner);
        assertThat(post(root + "/closeout/complete", body, owner).path("record").path("version"))
                .isEqualTo(done.path("record").path("version"));
        assertThat(get(root, owner).path("status").asText()).isEqualTo("TODO");
        assertThat(
                        get(root + "/closeout", owner)
                                .path("record")
                                .path("content")
                                .path("wikiDocumentId")
                                .asLong())
                .isEqualTo(wikiId);
        var timeline = get(root + "/timeline?size=100", owner).path("content");
        Set<String> actions = new HashSet<>();
        timeline.forEach(i -> actions.add(i.path("event").path("actionType").asText()));
        assertThat(actions)
                .contains(
                        "DEFINITION_CONFIRM",
                        "DECISION_SUPERSEDE",
                        "VERIFICATION_CREATE",
                        "WIKI_CREATE",
                        "CLOSEOUT_COMPLETE");
        for (String path :
                List.of(
                        root + "/decisions/" + d.path("id").asLong(),
                        root + "/verifications/" + failed.path("id").asLong(),
                        "/api/wikis/" + wikiId,
                        root + "/closeout")) request("GET", path, null, stranger, 403);
        put(
                "/api/wikis/" + wikiId,
                Map.of("title", "人工审阅复盘", "content", "新的风险说明", "requirementId", req),
                owner);
        assertThat(get(root + "/closeout", owner).path("needsReview").asBoolean()).isTrue();
        var reopened =
                post(
                        root + "/closeout/reopen",
                        Map.of("version", done.path("record").path("version").asLong()),
                        owner);
        post(root + "/closeout/complete", closeout(reopened, wikiId), owner);
        assertThat(get(root + "/closeout", owner).path("needsReview").asBoolean()).isFalse();
        assertThat(get("/api/wikis/" + wikiId, owner).path("documentType").asText())
                .isEqualTo("RETROSPECTIVE");
    }
}
