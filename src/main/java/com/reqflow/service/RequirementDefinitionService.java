package com.reqflow.service;

import com.reqflow.dto.DefinitionResponse;
import com.reqflow.dto.RequirementDefinition;
import com.reqflow.dto.RequirementDefinition.SuccessCriterion;
import com.reqflow.entity.Requirement;
import com.reqflow.repository.RequirementRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequirementDefinitionService {
    private final RequirementRepository repository;
    private final RequirementAccessService accessService;
    private final ActivityLogService activityLogService;

    public RequirementDefinitionService(
            RequirementRepository repository,
            RequirementAccessService accessService,
            ActivityLogService activityLogService) {
        this.repository = repository;
        this.accessService = accessService;
        this.activityLogService = activityLogService;
    }

    @Transactional(readOnly = true)
    public DefinitionResponse get(Long id, Long userId) {
        authorize(id, userId);
        return response(repository.findById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public DefinitionResponse save(
            Long id, Long userId, Long version, RequirementDefinition definition) {
        Requirement requirement = lockedRequirement(id, userId);
        checkVersion(requirement, version);
        RequirementDefinition normalized = normalize(definition);
        if (normalized.equals(normalize(requirement.getDefinition()))) {
            return response(requirement);
        }
        boolean wasConfirmed = requirement.getDefinitionConfirmedAt() != null;
        requirement.setDefinition(normalized);
        requirement.setDefinitionConfirmedAt(null);
        requirement.setDefinitionConfirmedBy(null);
        requirement.setDefinitionVersion(requirement.getDefinitionVersion() + 1);
        requirement.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(requirement);
        activityLogService.record(
                id,
                userId,
                "REQUIREMENT",
                id,
                "DEFINITION_UPDATE",
                wasConfirmed ? "修改了问题定义，需要重新确认" : "更新了问题定义");
        return response(requirement);
    }

    @Transactional
    public DefinitionResponse confirm(Long id, Long userId, Long version) {
        Requirement requirement = lockedRequirement(id, userId);
        checkVersion(requirement, version);
        RequirementDefinition definition = normalize(requirement.getDefinition());
        if (!complete(definition)) {
            throw badRequest("请填写要解决的问题、期望结果和至少一条成功标准后再确认");
        }
        if (requirement.getDefinitionConfirmedAt() == null) {
            requirement.setDefinitionConfirmedAt(Instant.now());
            requirement.setDefinitionConfirmedBy(userId);
            requirement.setDefinitionVersion(requirement.getDefinitionVersion() + 1);
            requirement.setUpdatedAt(LocalDateTime.now());
            repository.saveAndFlush(requirement);
            activityLogService.record(
                    id, userId, "REQUIREMENT", id, "DEFINITION_CONFIRM", "确认了问题定义");
        }
        return response(requirement);
    }

    private void authorize(Long id, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        // Access is checked before existence to avoid disclosing private resource IDs.
        accessService.requireRequirementOwner(id, userId);
    }

    private Requirement lockedRequirement(Long id, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        // Lock before loading through the access service so the persistence context cannot
        // retain an older definition/version while waiting for another writer to commit.
        Requirement requirement =
                repository
                        .findForDefinitionUpdate(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.FORBIDDEN, "Permission denied"));
        accessService.requireRequirementOwner(id, userId);
        return requirement;
    }

    private void checkVersion(Requirement requirement, Long version) {
        if (version == null || version < 0) throw badRequest("缺少有效的定义版本");
        if (version != requirement.getDefinitionVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "问题定义已被其他操作修改，请保留当前输入并重新加载比较");
        }
    }

    private DefinitionResponse response(Requirement requirement) {
        RequirementDefinition definition = normalize(requirement.getDefinition());
        boolean started =
                !definition.problemStatement().isEmpty()
                        || !definition.targetOutcome().isEmpty()
                        || !definition.constraints().isEmpty()
                        || !definition.assumptions().isEmpty()
                        || !definition.outOfScope().isEmpty()
                        || !definition.successCriteria().isEmpty();
        String state =
                requirement.getDefinitionConfirmedAt() != null && complete(definition)
                        ? "CONFIRMED"
                        : started ? "IN_PROGRESS" : "NOT_STARTED";
        return new DefinitionResponse(
                definition,
                state,
                requirement.getDefinitionVersion(),
                requirement.getDefinitionConfirmedAt(),
                requirement.getDefinitionConfirmedBy());
    }

    private boolean complete(RequirementDefinition definition) {
        return !definition.problemStatement().isEmpty()
                && !definition.targetOutcome().isEmpty()
                && !definition.successCriteria().isEmpty();
    }

    private RequirementDefinition normalize(RequirementDefinition input) {
        if (input == null) throw badRequest("问题定义不能为空");
        List<SuccessCriterion> criteria = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        if (input.successCriteria() != null) {
            if (input.successCriteria().size() > 50) throw badRequest("成功标准最多 50 条");
            for (SuccessCriterion criterion : input.successCriteria()) {
                if (criterion == null) throw badRequest("成功标准不能为空");
                String id = text(criterion.id(), 64);
                String description = text(criterion.description(), 1000);
                if (!id.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(id)) {
                    throw badRequest("成功标准 ID 必须有效且不重复");
                }
                if (description.isEmpty()) throw badRequest("成功标准描述不能为空");
                criteria.add(
                        new SuccessCriterion(
                                id,
                                description,
                                text(criterion.suggestedMethod(), 1000),
                                text(criterion.targetValue(), 1000)));
            }
        }
        return new RequirementDefinition(
                text(input.problemStatement(), 10000),
                text(input.targetOutcome(), 10000),
                lines(input.constraints()),
                lines(input.assumptions()),
                lines(input.outOfScope()),
                List.copyOf(criteria));
    }

    private List<String> lines(List<String> input) {
        if (input == null) return List.of();
        if (input.size() > 50) throw badRequest("每组内容最多 50 条");
        List<String> result = new ArrayList<>();
        for (String item : input) {
            if (item == null) throw badRequest("列表项不能为空");
            String normalized = text(item, 1000);
            if (normalized.isEmpty()) throw badRequest("列表项不能为空");
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private String text(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() > maxLength) throw badRequest("字段内容超过长度限制：" + maxLength);
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "需求不存在");
    }
}
