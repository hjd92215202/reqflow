package com.reqflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.dto.*;
import com.reqflow.entity.*;
import com.reqflow.repository.*;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerificationService {
    private static final Set<String> RESULTS =
            Set.of("PASS", "FAIL", "PARTIAL", "INCONCLUSIVE", "WAIVED");
    private final VerificationRecordRepository records;
    private final RequirementRepository requirements;
    private final StageRepository stages;
    private final SubTaskRepository tasks;
    private final RequirementAccessService access;
    private final ActivityLogService activity;
    private final SubTaskService taskService;
    private final ObjectMapper mapper;

    public VerificationService(
            VerificationRecordRepository records,
            RequirementRepository requirements,
            StageRepository stages,
            SubTaskRepository tasks,
            RequirementAccessService access,
            ActivityLogService activity,
            SubTaskService taskService,
            ObjectMapper mapper) {
        this.records = records;
        this.requirements = requirements;
        this.stages = stages;
        this.tasks = tasks;
        this.access = access;
        this.activity = activity;
        this.taskService = taskService;
        this.mapper = mapper;
    }

    private void authorize(Long requirement, Long user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        access.requireRequirementOwner(requirement, user);
    }

    private Requirement lock(Long requirement, Long user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        var entity = requirements.findForDefinitionUpdate(requirement).orElseThrow(this::missing);
        authorize(requirement, user);
        return entity;
    }

    @Transactional(readOnly = true)
    public VerificationResponse get(Long requirement, Long id, Long user) {
        authorize(requirement, user);
        return response(find(requirement, id));
    }

    private VerificationRecord find(Long requirement, Long id) {
        return records.findByIdAndRequirementId(id, requirement).orElseThrow(this::missing);
    }

    @Transactional(readOnly = true)
    public Page<VerificationResponse> list(
            Long requirement,
            Long user,
            Long stage,
            Long task,
            String criterion,
            String result,
            Boolean valid,
            int page,
            int size) {
        authorize(requirement, user);
        if (result != null && !RESULTS.contains(result)) throw bad("验证结论无效");
        return records.findAll(
                        (root, query, cb) -> {
                            var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
                            filters.add(cb.equal(root.get("requirementId"), requirement));
                            if (stage != null) filters.add(cb.equal(root.get("stageId"), stage));
                            if (task != null) filters.add(cb.equal(root.get("subTaskId"), task));
                            if (criterion != null)
                                filters.add(cb.equal(root.get("successCriterionId"), criterion));
                            if (result != null)
                                filters.add(cb.equal(root.get("resultStatus"), result));
                            if (valid != null) {
                                var isValid =
                                        cb.and(
                                                cb.isNull(root.get("invalidatedAt")),
                                                cb.isNull(root.get("voidedAt")));
                                filters.add(valid ? isValid : cb.not(isValid));
                            }
                            return cb.and(
                                    filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
                        },
                        PageRequest.of(
                                Math.max(0, page),
                                Math.min(100, Math.max(1, size)),
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(this::response);
    }

    @Transactional
    public VerificationResponse create(
            Long requirement, Long user, VerificationCreateRequest input) {
        var owner = lock(requirement, user);
        String key = requestId(input.clientRequestId());
        String fingerprint = fingerprint(input);
        var retry = records.findByRequirementIdAndClientRequestId(requirement, key);
        if (retry.isPresent()) {
            if (!retry.get().getRequestFingerprint().equals(fingerprint))
                throw conflict("本次提交标识已用于其他内容，请作为新记录保存");
            return response(retry.get());
        }
        var value = normalize(input.content());
        var entity = new VerificationRecord();
        entity.setRequirementId(requirement);
        Long stageId = input.stageId();
        if (input.subTaskId() != null) {
            var task =
                    tasks.findForVerification(input.subTaskId())
                            .orElseThrow(() -> conflict("关联任务不存在或已删除，请比较当前上下文"));
            if (stageId != null && !stageId.equals(task.getStageId())) throw bad("任务与阶段不一致");
            stageId = task.getStageId();
            if (!Objects.equals(
                            optional(input.taskDeliverable(), 10000),
                            optional(task.getDeliverable(), 10000))
                    || !Objects.equals(
                            optional(input.taskCompletionCriteria(), 10000),
                            optional(task.getCompletionCriteria(), 10000)))
                throw conflict("任务交付标准已变化，请保留输入并比较最新标准");
            entity.setSubTaskId(task.getId());
            entity.setSubTaskTitle(task.getTitle());
            entity.setTaskDeliverable(task.getDeliverable());
            entity.setTaskCompletionCriteria(task.getCompletionCriteria());
        }
        if (stageId != null) {
            var stage =
                    stages.findById(stageId).orElseThrow(() -> conflict("关联阶段不存在或已删除，请比较当前上下文"));
            if (!requirement.equals(stage.getRequirementId())) throw bad("关联阶段或任务不属于当前需求");
            entity.setStageId(stageId);
            entity.setStageTitle(stage.getTitle());
        }
        if (input.successCriterionId() != null) {
            if (input.definitionVersion() == null
                    || input.definitionVersion() != owner.getDefinitionVersion())
                throw conflict("问题定义已变化，请保留输入并比较当前标准");
            var criterion =
                    criteria(owner).stream()
                            .filter(c -> c.id().equals(input.successCriterionId()))
                            .findFirst()
                            .orElseThrow(() -> bad("成功标准不属于当前需求或已删除"));
            if (!criterion.description().equals(value.criterionSnapshot()))
                throw conflict("成功标准已变化，请保留输入并比较最新标准");
            entity.setSuccessCriterionId(criterion.id());
            entity.setCriterionMethod(criterion.suggestedMethod());
            entity.setCriterionTarget(criterion.targetValue());
        }
        entity.setCriterionSnapshot(value.criterionSnapshot());
        entity.setMethod(value.method());
        entity.setExpectedResult(value.expectedResult());
        entity.setActualResult(value.actualResult());
        entity.setResultStatus(value.resultStatus());
        entity.setWaiverReason(value.waiverReason());
        entity.setEvidence(value.evidence());
        entity.setVerifiedAt(value.verifiedAt());
        entity.setVerifiedBy(user);
        entity.setCreatedAt(Instant.now());
        entity.setClientRequestId(key);
        entity.setRequestFingerprint(fingerprint);
        records.saveAndFlush(entity);
        activity.record(
                requirement,
                user,
                "VERIFICATION",
                entity.getId(),
                "VERIFICATION_CREATE",
                "记录了验证「"
                        + auditSnapshot(entity.getCriterionSnapshot())
                        + "」："
                        + entity.getResultStatus());
        return response(entity);
    }

    @Transactional
    public VerificationResponse voidRecord(Long requirement, Long id, Long user, String reason) {
        lock(requirement, user);
        var entity = find(requirement, id);
        if (entity.getVoidedAt() != null) throw conflict("这条记录已经作废");
        entity.setVoidReason(required(reason, 10000, "作废理由"));
        entity.setVoidedBy(user);
        entity.setVoidedAt(Instant.now());
        records.saveAndFlush(entity);
        activity.record(
                requirement,
                user,
                "VERIFICATION",
                id,
                "VERIFICATION_VOID",
                "作废了验证记录 #" + id + "（历史保留）");
        return response(entity);
    }

    public record RepairRequest(
            String clientRequestId,
            Long stageId,
            String title,
            String deliverable,
            String completionCriteria) {}

    @Transactional
    public SubTask createRepair(Long requirement, Long id, Long user, RepairRequest input) {
        lock(requirement, user);
        var source = find(requirement, id);
        if (!Set.of("FAIL", "PARTIAL", "INCONCLUSIVE").contains(source.getResultStatus()))
            throw bad("仅失败、部分通过或不确定的记录可创建修复任务");
        String key = requestId(input.clientRequestId());
        Long stageId = source.getStageId() != null ? source.getStageId() : input.stageId();
        if (stageId == null) throw bad("请为修复任务选择当前需求的阶段");
        var stage = stages.findById(stageId).orElseThrow(() -> bad("修复阶段不存在"));
        if (!requirement.equals(stage.getRequirementId())) throw bad("修复阶段不属于当前需求");
        if (source.getStageId() != null
                && input.stageId() != null
                && !stageId.equals(input.stageId())) throw bad("修复任务须保留原阶段上下文");
        String title = required(input.title(), 255, "修复任务标题");
        String deliverable = optional(input.deliverable(), 10000),
                completion = optional(input.completionCriteria(), 10000);
        var retry = tasks.findByRepairVerificationIdAndRepairRequestId(id, key);
        if (retry.isPresent()) {
            var task = retry.get();
            if (!Objects.equals(task.getStageId(), stageId)
                    || !Objects.equals(task.getTitle(), title)
                    || !Objects.equals(task.getDeliverable(), deliverable)
                    || !Objects.equals(task.getCompletionCriteria(), completion))
                throw conflict("修复提交标识已用于其他内容");
            return task;
        }
        var task = new SubTask();
        task.setStageId(stageId);
        task.setTitle(title);
        task.setStatus("TODO");
        task.setDeliverable(deliverable);
        task.setCompletionCriteria(completion);
        task.setRepairVerificationId(id);
        task.setRepairRequestId(key);
        task.setNote(
                "来源：验证 #"
                        + id
                        + "\n标准："
                        + source.getCriterionSnapshot()
                        + "\n结论："
                        + source.getResultStatus()
                        + "\n实际结果："
                        + source.getActualResult());
        var created = taskService.createSubTask(task, user);
        activity.record(
                requirement,
                user,
                "VERIFICATION",
                id,
                "VERIFICATION_REPAIR",
                "从验证 #" + id + " 创建修复任务 #" + created.getId());
        return created;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VerificationSummary summary(Long requirement, Long user) {
        authorize(requirement, user);
        var owner = requirements.findById(requirement).orElseThrow(this::missing);
        var criterionLatest =
                records.latestCriteria(requirement).stream()
                        .collect(
                                Collectors.toMap(
                                        VerificationRecordRepository.Latest::getSuccessCriterionId,
                                        Function.identity()));
        var taskLatest =
                records.latestTasks(requirement).stream()
                        .collect(
                                Collectors.toMap(
                                        VerificationRecordRepository.Latest::getSubTaskId,
                                        Function.identity()));
        var distribution = new LinkedHashMap<String, Long>();
        for (String result :
                List.of("UNVERIFIED", "PASS", "FAIL", "PARTIAL", "INCONCLUSIVE", "WAIVED"))
            distribution.put(result, 0L);
        var criterionSummary =
                criteria(owner).stream()
                        .map(
                                c -> {
                                    var latest = criterionLatest.get(c.id());
                                    String status =
                                            latest == null
                                                    ? "UNVERIFIED"
                                                    : latest.getResultStatus();
                                    distribution.put(status, distribution.get(status) + 1);
                                    return new VerificationSummary.Criterion(
                                            c.id(),
                                            c.description(),
                                            c.suggestedMethod(),
                                            c.targetValue(),
                                            latest(latest));
                                })
                        .toList();
        var stageList = stages.findByRequirementIdOrderByIdAsc(requirement);
        var taskList =
                stageList.isEmpty()
                        ? List.<SubTask>of()
                        : tasks.findByStageIdInOrderByIdAsc(
                                stageList.stream().map(Stage::getId).toList());
        var pending = new HashMap<Long, Long>();
        var taskSummary =
                taskList.stream()
                        .map(
                                task -> {
                                    var latest = taskLatest.get(task.getId());
                                    if ("DONE".equals(task.getStatus()) && latest == null)
                                        pending.merge(task.getStageId(), 1L, Long::sum);
                                    return new VerificationSummary.Task(
                                            task.getId(),
                                            task.getStageId(),
                                            task.getTitle(),
                                            task.getStatus(),
                                            task.getDeliverable(),
                                            task.getCompletionCriteria(),
                                            latest(latest));
                                })
                        .toList();
        return new VerificationSummary(
                owner.getDefinitionVersion(),
                criterionSummary,
                distribution,
                taskSummary,
                pending.values().stream().mapToLong(Long::longValue).sum(),
                stageList.stream()
                        .map(
                                s ->
                                        new VerificationSummary.StageCount(
                                                s.getId(),
                                                s.getTitle(),
                                                pending.getOrDefault(s.getId(), 0L)))
                        .toList());
    }

    private VerificationSummary.Latest latest(VerificationRecordRepository.Latest record) {
        return record == null
                ? null
                : new VerificationSummary.Latest(
                        record.getId(), record.getResultStatus(), record.getCreatedAt());
    }

    private List<RequirementDefinition.SuccessCriterion> criteria(Requirement requirement) {
        return requirement.getDefinition().successCriteria() == null
                ? List.of()
                : requirement.getDefinition().successCriteria();
    }

    private VerificationContent normalize(VerificationContent input) {
        if (input == null) throw bad("验证内容不能为空");
        if (!RESULTS.contains(input.resultStatus() == null ? "" : input.resultStatus()))
            throw bad("验证结论无效");
        String reason = optional(input.waiverReason(), 10000);
        if ("WAIVED".equals(input.resultStatus()) && reason == null) throw bad("豁免必须填写理由");
        var evidence = new ArrayList<VerificationContent.Evidence>();
        if (input.evidence() != null) {
            if (input.evidence().size() > 20) throw bad("证据最多 20 项");
            for (var item : input.evidence()) {
                if (item == null) throw bad("证据不能为空");
                String url = optional(item.url(), 2000),
                        description = optional(item.description(), 10000);
                if (url == null && description == null) throw bad("证据须填写链接或文字说明");
                if (url != null && !safeUrl(url)) throw bad("证据链接仅支持 HTTP(S) 或内部 Wiki 路径");
                evidence.add(
                        new VerificationContent.Evidence(
                                optional(item.title(), 1000), url, description));
            }
        }
        return new VerificationContent(
                required(input.criterionSnapshot(), 10000, "验证标准"),
                required(input.method(), 10000, "验证方法"),
                optional(input.expectedResult(), 10000),
                required(input.actualResult(), 10000, "实际结果"),
                input.resultStatus(),
                reason,
                List.copyOf(evidence),
                input.verifiedAt());
    }

    private boolean safeUrl(String url) {
        try {
            var uri = URI.create(url);
            return (Set.of("http", "https")
                                    .contains(
                                            Objects.toString(uri.getScheme(), "")
                                                    .toLowerCase(Locale.ROOT))
                            && uri.getHost() != null)
                    || (uri.getScheme() == null
                            && uri.getRawAuthority() == null
                            && "/wiki".equals(uri.getPath()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private String auditSnapshot(String value) {
        int count = value.codePointCount(0, value.length());
        return count <= 800 ? value : value.substring(0, value.offsetByCodePoints(0, 800)) + "…";
    }

    private String requestId(String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value))
                throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException invalid) {
            throw bad("缺少有效的提交标识");
        }
    }

    private String fingerprint(VerificationCreateRequest input) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(mapper.writeValueAsBytes(input)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to fingerprint verification", error);
        }
    }

    private String optional(String input, int max) {
        if (input == null) return null;
        if (input.length() > max) throw bad("字段长度超过限制：" + max);
        return input.trim().isEmpty() ? null : input.trim();
    }

    private String required(String input, int max, String label) {
        var value = optional(input, max);
        if (value == null) throw bad(label + "不能为空");
        return value;
    }

    private VerificationResponse response(VerificationRecord e) {
        return new VerificationResponse(
                e.getId(),
                e.getRequirementId(),
                e.getStageId(),
                e.getSubTaskId(),
                e.getStageTitle(),
                e.getSubTaskTitle(),
                e.getSuccessCriterionId(),
                e.getCriterionMethod(),
                e.getCriterionTarget(),
                e.getTaskDeliverable(),
                e.getTaskCompletionCriteria(),
                new VerificationContent(
                        e.getCriterionSnapshot(),
                        e.getMethod(),
                        e.getExpectedResult(),
                        e.getActualResult(),
                        e.getResultStatus(),
                        e.getWaiverReason(),
                        e.getEvidence(),
                        e.getVerifiedAt()),
                e.getVerifiedBy(),
                e.getCreatedAt(),
                e.getInvalidatedAt(),
                e.getInvalidationReason(),
                e.getVoidedAt(),
                e.getVoidedBy(),
                e.getVoidReason(),
                e.getInvalidatedAt() == null && e.getVoidedAt() == null);
    }

    private ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问此验证或记录不存在");
    }
}
