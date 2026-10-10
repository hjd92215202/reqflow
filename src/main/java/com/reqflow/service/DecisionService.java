package com.reqflow.service;

import com.reqflow.dto.*;
import com.reqflow.entity.DecisionRecord;
import com.reqflow.repository.DecisionRecordRepository;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DecisionService {
    private final DecisionRecordRepository repository;
    private final StageRepository stages;
    private final SubTaskRepository tasks;
    private final RequirementAccessService access;
    private final ActivityLogService activity;

    public DecisionService(
            DecisionRecordRepository repository,
            StageRepository stages,
            SubTaskRepository tasks,
            RequirementAccessService access,
            ActivityLogService activity) {
        this.repository = repository;
        this.stages = stages;
        this.tasks = tasks;
        this.access = access;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public Page<DecisionResponse> list(
            Long requirementId,
            Long userId,
            Long stageId,
            Long taskId,
            String status,
            int page,
            int size) {
        authorize(requirementId, userId);
        if (status != null
                && !Set.of("PROPOSED", "ACCEPTED", "REJECTED", "SUPERSEDED").contains(status))
            throw bad("决策状态无效");
        return repository
                .findAll(
                        (root, query, cb) -> {
                            var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
                            filters.add(cb.equal(root.get("requirementId"), requirementId));
                            if (stageId != null)
                                filters.add(cb.equal(root.get("stageId"), stageId));
                            if (taskId != null)
                                filters.add(cb.equal(root.get("subTaskId"), taskId));
                            if (status != null) filters.add(cb.equal(root.get("status"), status));
                            return cb.and(
                                    filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
                        },
                        PageRequest.of(
                                Math.max(0, page),
                                Math.min(100, Math.max(1, size)),
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public DecisionResponse get(Long requirementId, Long id, Long userId) {
        authorize(requirementId, userId);
        return response(
                repository.findByIdAndRequirementId(id, requirementId).orElseThrow(this::missing));
    }

    @Transactional
    public DecisionResponse create(Long requirementId, Long userId, DecisionCreateRequest request) {
        authorize(requirementId, userId);
        var entity = new DecisionRecord();
        entity.setRequirementId(requirementId);
        bindContext(entity, request.stageId(), request.subTaskId());
        apply(entity, normalize(request.content()));
        created(entity, userId);
        repository.saveAndFlush(entity);
        record(entity, userId, "DECISION_CREATE", "记录了决策");
        return response(entity);
    }

    @Transactional
    public DecisionResponse update(
            Long requirementId, Long id, Long userId, DecisionWriteRequest request) {
        var entity = locked(requirementId, id, userId);
        checkVersion(entity, request.version());
        if ("SUPERSEDED".equals(entity.getStatus())) throw conflict("旧决策已被替代，请打开替代决策");
        var normalized = normalize(request.content());
        if (normalized.equals(content(entity))) return response(entity);
        String priorStatus = entity.getStatus();
        apply(entity, normalized);
        changed(entity, userId);
        repository.saveAndFlush(entity);
        String action =
                !priorStatus.equals(entity.getStatus())
                        ? "ACCEPTED".equals(entity.getStatus())
                                ? "DECISION_ACCEPT"
                                : "REJECTED".equals(entity.getStatus())
                                        ? "DECISION_REJECT"
                                        : "DECISION_UPDATE"
                        : "DECISION_UPDATE";
        record(entity, userId, action, "修订了决策（" + priorStatus + " → " + entity.getStatus() + "）");
        return response(entity);
    }

    @Transactional
    public DecisionResponse supersede(
            Long requirementId, Long id, Long userId, DecisionWriteRequest request) {
        var source = locked(requirementId, id, userId);
        checkVersion(source, request.version());
        if (!"ACCEPTED".equals(source.getStatus())) throw conflict("只能替代当前已采纳的决策");
        var normalized = normalize(request.content());
        if (!"ACCEPTED".equals(normalized.status())) throw bad("替代决策必须明确采纳最终选择");
        var replacement = new DecisionRecord();
        replacement.setRequirementId(requirementId);
        replacement.setStageId(source.getStageId());
        replacement.setSubTaskId(source.getSubTaskId());
        replacement.setStageTitle(source.getStageTitle());
        replacement.setSubTaskTitle(source.getSubTaskTitle());
        replacement.setSupersedesDecisionId(source.getId());
        apply(replacement, normalized);
        created(replacement, userId);
        repository.saveAndFlush(replacement);
        source.setStatus("SUPERSEDED");
        source.setSupersededByDecisionId(replacement.getId());
        changed(source, userId);
        repository.saveAndFlush(source);
        record(replacement, userId, "DECISION_CREATE", "创建了替代决策（替代 #" + source.getId() + "）");
        record(source, userId, "DECISION_SUPERSEDE", "决策被 #" + replacement.getId() + " 替代");
        return response(replacement);
    }

    private void bindContext(DecisionRecord entity, Long stageId, Long taskId) {
        if (taskId != null) {
            var task = tasks.findById(taskId).orElseThrow(() -> bad("关联任务不存在"));
            if (stageId != null && !stageId.equals(task.getStageId())) throw bad("任务与阶段不一致");
            stageId = task.getStageId();
            entity.setSubTaskId(taskId);
            entity.setSubTaskTitle(task.getTitle());
        }
        if (stageId != null) {
            var stage = stages.findById(stageId).orElseThrow(() -> bad("关联阶段不存在"));
            if (!entity.getRequirementId().equals(stage.getRequirementId()))
                throw bad("关联阶段或任务不属于当前需求");
            entity.setStageId(stageId);
            entity.setStageTitle(stage.getTitle());
        }
    }

    private void authorize(Long requirementId, Long userId) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        access.requireRequirementOwner(requirementId, userId);
    }

    private DecisionRecord locked(Long requirementId, Long id, Long userId) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        // Load under lock before access checks to avoid a stale persistence context.
        var entity = repository.findForUpdate(requirementId, id).orElseThrow(this::missing);
        authorize(requirementId, userId);
        return entity;
    }

    private void checkVersion(DecisionRecord entity, Long version) {
        if (version == null || version < 0) throw bad("缺少有效的决策版本");
        if (version != entity.getVersion()) throw conflict("决策已被其他操作修改，请保留输入并重新加载比较");
    }

    private void created(DecisionRecord entity, Long userId) {
        entity.setCreatedBy(userId);
        entity.setCreatedAt(LocalDateTime.now());
        changed(entity, userId);
        entity.setVersion(0);
    }

    private void changed(DecisionRecord entity, Long userId) {
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setVersion(entity.getVersion() + 1);
    }

    private void record(DecisionRecord entity, Long userId, String action, String summary) {
        activity.record(
                entity.getRequirementId(),
                userId,
                "DECISION",
                entity.getId(),
                action,
                summary + "「" + entity.getTitle() + "」");
    }

    private DecisionContent normalize(DecisionContent input) {
        if (input == null) throw bad("决策内容不能为空");
        String title = required(input.title(), 255, "决策标题");
        String context = required(input.context(), 10000, "问题背景");
        String chosen = text(input.chosenOption(), 10000);
        String rationale = text(input.rationale(), 10000);
        String status = input.status() == null ? "PROPOSED" : input.status();
        if (!Set.of("PROPOSED", "ACCEPTED", "REJECTED").contains(status))
            throw bad("决策状态无效；替代请使用专用操作");
        if ("ACCEPTED".equals(status) && (chosen == null || rationale == null))
            throw bad("采纳决策须填写最终选择和理由");
        if ("REJECTED".equals(status) && rationale == null) throw bad("否决决策须填写理由");
        String confidence = text(input.confidence(), 16);
        if (confidence != null && !Set.of("LOW", "MEDIUM", "HIGH").contains(confidence))
            throw bad("信心程度无效");
        var options = new ArrayList<DecisionContent.Option>();
        if (input.options() != null) {
            if (input.options().size() > 20) throw bad("候选方案最多 20 个");
            for (var option : input.options()) {
                if (option == null) throw bad("候选方案不能为空");
                options.add(
                        new DecisionContent.Option(
                                required(option.name(), 1000, "方案名称"),
                                text(option.pros(), 10000),
                                text(option.cons(), 10000)));
            }
        }
        return new DecisionContent(
                title,
                context,
                List.copyOf(options),
                chosen,
                rationale,
                lines(input.assumptions()),
                confidence,
                status,
                input.reviewDate(),
                ai(input.aiAssistance()));
    }

    private DecisionContent.AiAssistance ai(DecisionContent.AiAssistance input) {
        if (input == null) return null;
        var phases = input.phases() == null ? List.<String>of() : input.phases();
        if (phases.size() > 6
                || phases.stream()
                        .anyMatch(
                                phase ->
                                        phase == null
                                                || !Set.of(
                                                                "CLARIFICATION",
                                                                "COMPARISON",
                                                                "CODING",
                                                                "TEST_DESIGN",
                                                                "DOCUMENTATION",
                                                                "OTHER")
                                                        .contains(phase))
                || phases.stream().distinct().count() != phases.size()) throw bad("AI 参与环节无效或重复");
        String handling = text(input.handling(), 32);
        if (handling != null
                && !Set.of("ACCEPTED", "MODIFIED", "REJECTED", "BRAINSTORMING").contains(handling))
            throw bad("AI 建议处理方式无效");
        var result =
                new DecisionContent.AiAssistance(
                        List.copyOf(phases),
                        text(input.contribution(), 10000),
                        text(input.humanJudgment(), 10000),
                        handling,
                        text(input.verification(), 10000));
        return phases.isEmpty()
                        && result.contribution() == null
                        && result.humanJudgment() == null
                        && result.handling() == null
                        && result.verification() == null
                ? null
                : result;
    }

    private List<String> lines(List<String> input) {
        if (input == null) return List.of();
        if (input.size() > 50) throw bad("假设与风险最多 50 条");
        return input.stream().map(line -> required(line, 1000, "假设与风险")).toList();
    }

    private String text(String input, int max) {
        if (input == null) return null;
        if (input.length() > max) throw bad("字段长度超过限制：" + max);
        return input.trim().isEmpty() ? null : input.trim();
    }

    private String required(String input, int max, String label) {
        String result = text(input, max);
        if (result == null) throw bad(label + "不能为空");
        return result;
    }

    private DecisionContent content(DecisionRecord entity) {
        return new DecisionContent(
                entity.getTitle(),
                entity.getContext(),
                entity.getOptions(),
                entity.getChosenOption(),
                entity.getRationale(),
                entity.getAssumptions(),
                entity.getConfidence(),
                entity.getStatus(),
                entity.getReviewDate(),
                entity.getAiAssistance());
    }

    private void apply(DecisionRecord entity, DecisionContent content) {
        entity.setTitle(content.title());
        entity.setContext(content.context());
        entity.setOptions(content.options());
        entity.setChosenOption(content.chosenOption());
        entity.setRationale(content.rationale());
        entity.setAssumptions(content.assumptions());
        entity.setConfidence(content.confidence());
        entity.setStatus(content.status());
        entity.setReviewDate(content.reviewDate());
        entity.setAiAssistance(content.aiAssistance());
    }

    private DecisionResponse response(DecisionRecord entity) {
        return new DecisionResponse(
                entity.getId(),
                entity.getRequirementId(),
                entity.getStageId(),
                entity.getSubTaskId(),
                entity.getStageTitle(),
                entity.getSubTaskTitle(),
                content(entity),
                entity.getVersion(),
                entity.getSupersedesDecisionId(),
                entity.getSupersededByDecisionId(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问此决策或记录不存在");
    }
}
