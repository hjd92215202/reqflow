package com.reqflow.service;

import com.reqflow.entity.*;
import com.reqflow.repository.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequirementTimelineService {
    private final ActivityLogRepository logs;
    private final RequirementAccessService access;
    private final StageRepository stages;
    private final SubTaskRepository tasks;
    private final DecisionRecordRepository decisions;
    private final VerificationRecordRepository verifications;
    private final WikiDocumentRepository wikis;

    public RequirementTimelineService(
            ActivityLogRepository logs,
            RequirementAccessService access,
            StageRepository stages,
            SubTaskRepository tasks,
            DecisionRecordRepository decisions,
            VerificationRecordRepository verifications,
            WikiDocumentRepository wikis) {
        this.logs = logs;
        this.access = access;
        this.stages = stages;
        this.tasks = tasks;
        this.decisions = decisions;
        this.verifications = verifications;
        this.wikis = wikis;
    }

    public record Item(
            ActivityLog event,
            Long stageId,
            String stageTitle,
            Long subTaskId,
            String subTaskTitle,
            String currentStatus,
            boolean sourceAvailable) {}

    public record Result(
            List<Item> content,
            long totalElements,
            int totalPages,
            int number,
            int size,
            long snapshotId) {}

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Result list(
            Long requirement, Long user, String category, int page, int size, Long snapshotId) {
        access.requireRequirementOwner(requirement, user);
        String filter = category == null ? "ALL" : category;
        if (!Set.of("ALL", "OPERATION", "DECISION", "VERIFICATION", "KNOWLEDGE").contains(filter))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "活动类型无效");
        long snapshot = snapshotId == null ? logs.latestId(requirement) : snapshotId;
        if (snapshot < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页快照无效");
        Specification<ActivityLog> spec =
                (root, query, cb) -> {
                    var scope =
                            cb.and(
                                    cb.equal(root.get("requirementId"), requirement),
                                    cb.lessThanOrEqualTo(root.get("id"), snapshot));
                    return switch (filter) {
                        case "OPERATION" ->
                                cb.and(
                                        scope,
                                        cb.not(
                                                root.get("targetType")
                                                        .in("DECISION", "VERIFICATION", "WIKI")));
                        case "KNOWLEDGE" -> cb.and(scope, cb.equal(root.get("targetType"), "WIKI"));
                        case "DECISION", "VERIFICATION" ->
                                cb.and(scope, cb.equal(root.get("targetType"), filter));
                        default -> scope;
                    };
                };
        var result =
                logs.findAll(
                        spec,
                        PageRequest.of(
                                Math.max(0, page),
                                Math.min(100, Math.max(1, size)),
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        var rows = result.getContent();
        var taskMap = map(tasks.findAllById(ids(rows, "SUB_TASK")), SubTask::getId);
        var decisionMap = map(decisions.findAllById(ids(rows, "DECISION")), DecisionRecord::getId);
        var verificationMap =
                map(
                        verifications.findAllById(ids(rows, "VERIFICATION")),
                        VerificationRecord::getId);
        var wikiMap = map(wikis.findAllById(ids(rows, "WIKI")), WikiDocument::getId);
        var stageIds = new HashSet<>(ids(rows, "STAGE"));
        taskMap.values().forEach(t -> stageIds.add(t.getStageId()));
        var stageMap = map(stages.findAllById(stageIds), Stage::getId);
        var items =
                rows.stream()
                        .map(
                                e -> {
                                    Long stageId = null, taskId = null;
                                    String stageTitle = null, taskTitle = null, status = null;
                                    boolean available = false;
                                    switch (e.getTargetType()) {
                                        case "DECISION" -> {
                                            var d = decisionMap.get(e.getTargetId());
                                            if (d != null
                                                    && requirement.equals(d.getRequirementId())) {
                                                stageId = d.getStageId();
                                                taskId = d.getSubTaskId();
                                                stageTitle = d.getStageTitle();
                                                taskTitle = d.getSubTaskTitle();
                                                status = d.getStatus();
                                                available = true;
                                            }
                                        }
                                        case "VERIFICATION" -> {
                                            var v = verificationMap.get(e.getTargetId());
                                            if (v != null
                                                    && requirement.equals(v.getRequirementId())) {
                                                stageId = v.getStageId();
                                                taskId = v.getSubTaskId();
                                                stageTitle = v.getStageTitle();
                                                taskTitle = v.getSubTaskTitle();
                                                status =
                                                        v.getResultStatus()
                                                                + (v.getVoidedAt() != null
                                                                        ? " · 已作废"
                                                                        : v.getInvalidatedAt()
                                                                                        != null
                                                                                ? " · 已失效"
                                                                                : "");
                                                available = true;
                                            }
                                        }
                                        case "SUB_TASK" -> {
                                            var t = taskMap.get(e.getTargetId());
                                            var s = t == null ? null : stageMap.get(t.getStageId());
                                            if (s != null
                                                    && requirement.equals(s.getRequirementId())) {
                                                taskId = t.getId();
                                                taskTitle = t.getTitle();
                                                stageId = s.getId();
                                                stageTitle = s.getTitle();
                                                status = t.getStatus();
                                                available = true;
                                            }
                                        }
                                        case "STAGE" -> {
                                            var s = stageMap.get(e.getTargetId());
                                            if (s != null
                                                    && requirement.equals(s.getRequirementId())) {
                                                stageId = s.getId();
                                                stageTitle = s.getTitle();
                                                available = true;
                                            }
                                        }
                                        case "WIKI" -> {
                                            var w = wikiMap.get(e.getTargetId());
                                            available =
                                                    w != null
                                                            && requirement.equals(
                                                                    w.getRequirementId());
                                            status = available ? w.getDocumentType() : null;
                                        }
                                        case "REQUIREMENT" ->
                                                available = requirement.equals(e.getTargetId());
                                        default -> {}
                                    }
                                    return new Item(
                                            e,
                                            stageId,
                                            stageTitle,
                                            taskId,
                                            taskTitle,
                                            status,
                                            available);
                                })
                        .toList();
        return new Result(
                items,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                snapshot);
    }

    private List<Long> ids(List<ActivityLog> rows, String type) {
        return rows.stream()
                .filter(e -> type.equals(e.getTargetType()))
                .map(ActivityLog::getTargetId)
                .distinct()
                .toList();
    }

    private <T> Map<Long, T> map(List<T> values, Function<T, Long> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
