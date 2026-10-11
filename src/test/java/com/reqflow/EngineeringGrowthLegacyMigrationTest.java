package com.reqflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.util.JwtUtil;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
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
class EngineeringGrowthLegacyMigrationTest {
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
                var sql = connection.createStatement()) {
            sql.execute(
                    "INSERT INTO sys_user(id,username,password_hash)"
                            + " VALUES(9901,'legacy-migration','unused')");
            sql.execute(
                    "INSERT INTO req_workspace(id,name,owner_id) VALUES(9901,'legacy space',9901)");
            sql.execute(
                    "INSERT INTO req_project(id,workspace_id,name,identifier)"
                            + " VALUES(9901,9901,'legacy project','LEGACY')");
            sql.execute(
                    "INSERT INTO"
                        + " req_requirement(id,title,description,status,priority,creator_id,project_id,start_date,end_date)"
                        + " VALUES(9901,'legacy requirement','preserve"
                        + " description','IN_PROGRESS','HIGH',9901,9901,'2026-10-01','2026-10-31')");
            sql.execute(
                    "INSERT INTO req_stage(id,requirement_id,title) VALUES(9901,9901,'legacy"
                            + " stage')");
            sql.execute(
                    "INSERT INTO req_sub_task(id,stage_id,title,note,custom_fields,assignee)"
                            + " VALUES(9901,9901,'legacy parent','preserve"
                            + " note','{\"url\":\"legacy\"}','legacy owner')");
            sql.execute(
                    "INSERT INTO req_sub_task(id,stage_id,parent_id,title,note)"
                            + " VALUES(9902,9901,9901,'legacy child','preserve child')");
            sql.execute(
                    "INSERT INTO req_task_dependency(id,stage_id,predecessor_id,successor_id)"
                            + " VALUES(9901,9901,9901,9902)");
            sql.execute(
                    "INSERT INTO sys_todo(id,user_id,title,sub_task_id) VALUES(9901,9901,'legacy"
                            + " todo',9902)");
            sql.execute(
                    "INSERT INTO"
                        + " req_wiki_document(id,requirement_id,title,content,tags,creator_id,share_token)"
                        + " VALUES(9901,9901,'legacy wiki','preserve markdown','preserve"
                        + " tags',9901,'legacy-m7-share')");
        }
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired JdbcTemplate sql;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    String token() {
        return "Bearer " + JwtUtil.generateToken(9901L, "legacy-migration");
    }

    @Test
    void upgradePreservesHierarchyDependencyTodoWikiAndCanBeRepeated() {
        assertThat(
                        sql.queryForObject(
                                "SELECT version FROM flyway_schema_history WHERE success ORDER BY"
                                        + " installed_rank DESC LIMIT 1",
                                String.class))
                .isEqualTo("1.0.11");
        assertThat(
                        sql.queryForObject(
                                "SELECT parent_id FROM req_sub_task WHERE id=9902", Long.class))
                .isEqualTo(9901);
        assertThat(sql.queryForObject("SELECT sub_task_id FROM sys_todo WHERE id=9901", Long.class))
                .isEqualTo(9902);
        assertThat(
                        sql.queryForObject(
                                "SELECT successor_id FROM req_task_dependency WHERE id=9901",
                                Long.class))
                .isEqualTo(9902);
        assertThat(sql.queryForObject("SELECT note FROM req_sub_task WHERE id=9902", String.class))
                .isEqualTo("preserve child");
        assertThat(
                        sql.queryForObject(
                                "SELECT content FROM req_wiki_document WHERE id=9901",
                                String.class))
                .isEqualTo("preserve markdown");
        assertThat(
                        sql.queryForObject(
                                "SELECT share_token FROM req_wiki_document WHERE id=9901",
                                String.class))
                .isEqualTo("legacy-m7-share");
        assertThat(sql.queryForObject("SELECT count(*) FROM req_requirement_closeout", Long.class))
                .isZero();
        assertThat(
                        sql.queryForObject(
                                "SELECT count(*) FROM req_closeout_facts WHERE requirement_id=9901",
                                Long.class))
                .isEqualTo(1);
        var flyway =
                Flyway.configure()
                        .dataSource(
                                database.getJdbcUrl(),
                                database.getUsername(),
                                database.getPassword())
                        .load();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void legacyClientCanReadEditAndShareWithoutClearingEngineeringFields() throws Exception {
        mvc.perform(get("/api/requirements").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9901));
        mvc.perform(get("/api/requirements/9901/definition").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_STARTED"));
        mvc.perform(
                        put("/api/subtasks/9901")
                                .header("Authorization", token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"deliverable\":\"new"
                                                + " deliverable\",\"completionCriteria\":\"new"
                                                + " criteria\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/subtasks/9901")
                                .header("Authorization", token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverable").value("new deliverable"))
                .andExpect(jsonPath("$.completionCriteria").value("new criteria"))
                .andExpect(jsonPath("$.note").value("preserve note"))
                .andExpect(jsonPath("$.customFields.url").value("legacy"));
        mvc.perform(
                        put("/api/stages/9901")
                                .header("Authorization", token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"goal\":\"new goal\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/stages/9901")
                                .header("Authorization", token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value("new goal"));
        mvc.perform(
                        put("/api/wikis/9901")
                                .header("Authorization", token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"title\":\"legacy wiki\",\"content\":\"preserve"
                                            + " markdown\",\"tags\":\"preserve"
                                            + " tags\",\"requirementId\":9901,\"documentType\":\"RETROSPECTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        put("/api/wikis/9901")
                                .header("Authorization", token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"title\":\"legacy wiki\",\"content\":\"preserve"
                                                + " markdown\",\"tags\":\"preserve"
                                                + " tags\",\"requirementId\":9901}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("RETROSPECTIVE"));
        mvc.perform(get("/api/wikis/share/legacy-m7-share"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("preserve markdown"));
        mvc.perform(
                        put("/api/requirements/9901")
                                .header("Authorization", token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"title\":\"legacy"
                                            + " requirement\",\"description\":\"preserve"
                                            + " description\",\"status\":\"IN_PROGRESS\",\"priority\":\"HIGH\",\"projectId\":9901,\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(9901))
                .andExpect(jsonPath("$.startDate").value("2026-10-01"));
    }
}
