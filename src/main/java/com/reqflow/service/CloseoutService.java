package com.reqflow.service;

import com.reqflow.dto.*;
import com.reqflow.entity.*;
import com.reqflow.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CloseoutService {
    private final RequirementRepository requirements;
    private final RequirementCloseoutRepository closeouts;
    private final RequirementAccessService access;
    private final VerificationService verification;
    private final DecisionService decision;
    private final DecisionRecordRepository decisions;
    private final VerificationRecordRepository verifications;
    private final WikiDocumentRepository wikis;
    private final ActivityLogService activity;
    private final JdbcTemplate sql;

    public CloseoutService(
            RequirementRepository requirements,
            RequirementCloseoutRepository closeouts,
            RequirementAccessService access,
            VerificationService verification,
            DecisionService decision,
            DecisionRecordRepository decisions,
            VerificationRecordRepository verifications,
            WikiDocumentRepository wikis,
            ActivityLogService activity,
            JdbcTemplate sql) {
        this.requirements = requirements;
        this.closeouts = closeouts;
        this.access = access;
        this.verification = verification;
        this.decision = decision;
        this.decisions = decisions;
        this.verifications = verifications;
        this.wikis = wikis;
        this.activity = activity;
        this.sql = sql;
    }

    public record Save(Long version, String factsToken, CloseoutContent content) {}

    private record Snapshot(CloseoutResponse.Facts facts, String token) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CloseoutResponse get(Long req, Long user) {
        access.requireRequirementOwner(req, user);
        return response(req, closeouts.findById(req).orElse(null), snapshot(req, user));
    }

    @Transactional
    public CloseoutResponse save(Long req, Long user, Save input, boolean complete) {
        lock(req, user);
        var entity = closeouts.findById(req).orElse(null);
        var value = normalize(input.content());
        if (entity != null
                && input.version() != null
                && input.version() == entity.getVersion() - 1
                && Objects.equals(input.factsToken(), entity.getSavedFactsToken())
                && value.equals(content(entity))
                && (complete ? "COMPLETE" : "DRAFT").equals(entity.getStatus()))
            return response(req, entity, snapshot(req, user));
        long version = entity == null ? 0 : entity.getVersion();
        if (input.version() == null || input.version() != version)
            throw conflict("收尾记录已被其他操作修改，请比较最新记录");
        if (entity != null && "COMPLETE".equals(entity.getStatus())) throw conflict("请先重新打开收尾再修改");
        var current = snapshot(req, user);
        if (!current.token().equals(input.factsToken())) throw conflict("收尾事实已变化，请比较当前清单后继续");
        validateWiki(req, value.wikiDocumentId());
        if (complete) {
            if (value.outcome() == null || value.conclusion().isBlank()) throw bad("完成收尾需要目标结论和总结");
            var dispositions = new HashMap<String, CloseoutContent.Disposition>();
            value.dispositions().forEach(d -> dispositions.put(d.key(), d));
            for (var issue : current.facts().issues()) {
                var handling = dispositions.get(issue.key());
                if (handling == null || handling.handling() == null || handling.reason() == null)
                    throw bad("请为每个未解决项填写处理方式和理由：" + issue.title());
            }
        }
        if (entity == null) {
            entity = new RequirementCloseout();
            entity.setRequirementId(req);
        }
        entity.setContent(withWiki(value, null));
        entity.setWikiDocumentId(value.wikiDocumentId());
        entity.setVersion(version + 1);
        entity.setSavedFactsToken(current.token());
        entity.setStatus(complete ? "COMPLETE" : "DRAFT");
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(user);
        if (complete) {
            entity.setCompletedFactsToken(current.token());
            entity.setCompletedAt(entity.getUpdatedAt());
            entity.setCompletedBy(user);
        }
        closeouts.saveAndFlush(entity);
        activity.record(
                req,
                user,
                "CLOSEOUT",
                req,
                complete ? "CLOSEOUT_COMPLETE" : "CLOSEOUT_SAVE",
                complete ? "确认了收尾结论（需求状态未自动改变）" : "保存了收尾草稿");
        return response(req, entity, current);
    }

    @Transactional
    public CloseoutResponse reopen(Long req, Long user, Long version) {
        lock(req, user);
        var entity = closeouts.findById(req).orElseThrow(() -> bad("尚未完成收尾"));
        if (version != null
                && version == entity.getVersion() - 1
                && entity.getSavedFactsToken() == null
                && "DRAFT".equals(entity.getStatus()))
            return response(req, entity, snapshot(req, user));
        if (version == null || version != entity.getVersion()) throw conflict("收尾记录版本已变化");
        if (!"COMPLETE".equals(entity.getStatus())) throw conflict("收尾已经打开");
        entity.setStatus("DRAFT");
        entity.setVersion(entity.getVersion() + 1);
        entity.setCompletedFactsToken(null);
        entity.setSavedFactsToken(null);
        entity.setCompletedAt(null);
        entity.setCompletedBy(null);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(user);
        closeouts.saveAndFlush(entity);
        activity.record(req, user, "CLOSEOUT", req, "CLOSEOUT_REOPEN", "重新打开收尾，需要重新核对事实和确认");
        return response(req, entity, snapshot(req, user));
    }

    private void lock(Long req, Long user) {
        requirements
                .findForDefinitionUpdate(req)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "需求不存在或无权访问"));
        access.requireRequirementOwner(req, user);
        sql.queryForObject(
                "SELECT revision FROM req_closeout_facts WHERE requirement_id=? FOR UPDATE",
                Long.class,
                req);
    }

    private Snapshot snapshot(Long req, Long user) {
        var owner = requirements.findById(req).orElseThrow();
        var summary = verification.summary(req, user);
        var decisionList =
                decisions.findByRequirementIdOrderByIdAsc(req).stream()
                        .map(decision::response)
                        .toList();
        var issues = new ArrayList<CloseoutResponse.Issue>();
        if (owner.getDefinitionConfirmedAt() == null)
            issues.add(
                    new CloseoutResponse.Issue(
                            "DEFINITION",
                            "DEFINITION",
                            "问题定义尚未确认",
                            "UNCONFIRMED",
                            null,
                            null,
                            null,
                            null));
        if (summary.criteria().isEmpty())
            issues.add(
                    new CloseoutResponse.Issue(
                            "STANDARD:NONE",
                            "STANDARD",
                            "未设定成功标准",
                            "UNVERIFIED",
                            null,
                            null,
                            null,
                            null));
        for (var criterion : summary.criteria()) {
            String status =
                    criterion.latest() == null ? "UNVERIFIED" : criterion.latest().resultStatus();
            if (!"PASS".equals(status))
                issues.add(
                        new CloseoutResponse.Issue(
                                "STANDARD:" + criterion.id(),
                                "STANDARD",
                                criterion.description(),
                                status,
                                null,
                                null,
                                null,
                                criterion.id()));
        }
        for (var task : summary.tasks()) {
            if (!"DONE".equals(task.status()))
                issues.add(
                        new CloseoutResponse.Issue(
                                "TASK:" + task.id(),
                                "TASK",
                                task.title(),
                                task.status(),
                                task.stageId(),
                                task.id(),
                                null,
                                null));
            String result = task.latest() == null ? "UNVERIFIED" : task.latest().resultStatus();
            if ((task.latest() != null && !"PASS".equals(result))
                    || (task.latest() == null && "DONE".equals(task.status())))
                issues.add(
                        new CloseoutResponse.Issue(
                                "TASK_VERIFICATION:" + task.id(),
                                "TASK_VERIFICATION",
                                task.title() + " · 验证",
                                result,
                                task.stageId(),
                                task.id(),
                                null,
                                null));
        }
        var due = new ArrayList<Long>();
        for (var d : decisionList) {
            boolean overdue =
                    "ACCEPTED".equals(d.content().status())
                            && d.content().reviewDate() != null
                            && !d.content().reviewDate().isAfter(LocalDate.now(ZoneOffset.UTC));
            if (overdue) due.add(d.id());
            if (overdue || "PROPOSED".equals(d.content().status()))
                issues.add(
                        new CloseoutResponse.Issue(
                                "DECISION:" + d.id(),
                                "DECISION",
                                d.content().title(),
                                overdue ? "REVIEW_DUE" : "PROPOSED",
                                d.stageId(),
                                d.subTaskId(),
                                d.id(),
                                null));
        }
        var latestIds = new LinkedHashSet<Long>();
        summary.criteria()
                .forEach(
                        c -> {
                            if (c.latest() != null) latestIds.add(c.latest().id());
                        });
        summary.tasks()
                .forEach(
                        t -> {
                            if (t.latest() != null) latestIds.add(t.latest().id());
                        });
        var latest =
                verifications.findAllById(latestIds).stream()
                        .sorted(Comparator.comparing(VerificationRecord::getId))
                        .map(verification::response)
                        .toList();
        var wikiList =
                wikis.findByRequirementIdOrderByIdDesc(req).stream()
                        .map(
                                w ->
                                        new CloseoutResponse.Wiki(
                                                w.getId(), w.getTitle(), w.getDocumentType()))
                        .toList();
        long revision =
                sql.queryForObject(
                        "SELECT revision FROM req_closeout_facts WHERE requirement_id=?",
                        Long.class,
                        req);
        String token;
        try {
            token =
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    (revision + ":" + due)
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new Snapshot(
                new CloseoutResponse.Facts(
                        owner.getTitle(),
                        owner.getDescription(),
                        owner.getDefinition(),
                        owner.getDefinitionConfirmedAt(),
                        summary,
                        decisionList,
                        latest,
                        issues,
                        wikiList),
                token);
    }

    private CloseoutResponse response(Long req, RequirementCloseout e, Snapshot s) {
        var record =
                e == null
                        ? new CloseoutResponse.Record(
                                "NOT_STARTED", 0, CloseoutContent.empty(), null, null, null, null)
                        : new CloseoutResponse.Record(
                                e.getStatus(),
                                e.getVersion(),
                                content(e),
                                e.getCompletedAt(),
                                e.getCompletedBy(),
                                e.getUpdatedAt(),
                                e.getUpdatedBy());
        return new CloseoutResponse(
                req,
                record,
                s.facts(),
                s.token(),
                e != null
                        && "COMPLETE".equals(e.getStatus())
                        && !Objects.equals(e.getCompletedFactsToken(), s.token()));
    }

    private CloseoutContent content(RequirementCloseout e) {
        return withWiki(e.getContent(), e.getWikiDocumentId());
    }

    private CloseoutContent withWiki(CloseoutContent c, Long id) {
        return new CloseoutContent(
                c.outcome(),
                c.conclusion(),
                c.dispositions(),
                c.nextActions(),
                c.aiUse(),
                c.humanJudgment(),
                id);
    }

    private void validateWiki(Long req, Long id) {
        if (id != null
                && !wikis.findById(id).map(w -> req.equals(w.getRequirementId())).orElse(false))
            throw bad("请选择已保存且关联当前需求的 Wiki");
    }

    private CloseoutContent normalize(CloseoutContent c) {
        if (c == null) throw bad("收尾内容不能为空");
        String outcome = text(c.outcome(), 30);
        if (outcome != null
                && !Set.of("ACHIEVED", "PARTIAL", "NOT_ACHIEVED", "INCONCLUSIVE").contains(outcome))
            throw bad("目标结论无效");
        var entries =
                c.dispositions() == null
                        ? List.<CloseoutContent.Disposition>of()
                        : c.dispositions();
        if (entries.size() > 10000) throw bad("处置条目过多");
        var unique = new HashSet<String>();
        var normalized = new ArrayList<CloseoutContent.Disposition>();
        for (var d : entries) {
            if (d == null) throw bad("处置条目无效");
            String key = text(d.key(), 100);
            if (key == null || !unique.add(key)) throw bad("处置标识缺失或重复");
            String handling = text(d.handling(), 30);
            if (handling != null
                    && !Set.of("CONTINUE_FIX", "ACCEPT_RISK", "CANCEL_GOAL", "KEEP_OPEN")
                            .contains(handling)) throw bad("处理方式无效");
            normalized.add(new CloseoutContent.Disposition(key, handling, text(d.reason(), 10000)));
        }
        return new CloseoutContent(
                outcome,
                Objects.toString(text(c.conclusion(), 10000), ""),
                normalized,
                text(c.nextActions(), 10000),
                text(c.aiUse(), 10000),
                text(c.humanJudgment(), 10000),
                c.wikiDocumentId());
    }

    private String text(String value, int max) {
        if (value == null) return null;
        if (value.length() > max) throw bad("字段超过长度限制：" + max);
        return value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
