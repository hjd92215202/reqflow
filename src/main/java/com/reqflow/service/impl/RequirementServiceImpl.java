package com.reqflow.service.impl;

import com.reqflow.entity.Requirement;
import com.reqflow.repository.*;
import com.reqflow.service.RequirementService;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional // 优化引入：类级别显式声明写事务，确保主子表级联回滚
public class RequirementServiceImpl implements RequirementService {

    @Autowired private RequirementRepository requirementRepository;

    @Autowired private StageRepository stageRepository; // 优化引入：注入阶段仓库用于多级级联删除

    @Autowired private SubTaskRepository subTaskRepository; // 优化引入：注入子任务仓库用于多级级联删除

    @Autowired private DiscussionRepository discussionRepository; // 优化引入：注入日志仓库用于多级级联删除

    @Autowired private WikiDocumentRepository wikiDocumentRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Requirement> getRequirementsByCreator(Long creatorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return requirementRepository.findByCreatorIdOrderByIdDesc(creatorId, pageable);
    }

    @Override
    public Requirement createRequirement(Requirement requirement, Long creatorId) {
        requirement.setCreatorId(creatorId);
        if (requirement.getStatus() == null) requirement.setStatus("TODO");
        if (requirement.getPriority() == null) requirement.setPriority("MEDIUM");
        return requirementRepository.save(requirement);
    }

    @Override
    public Requirement updateRequirement(Long id, Requirement reqDetails) {
        var existing =
                requirementRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Requirement not found"));

        existing.setTitle(reqDetails.getTitle());
        existing.setDescription(reqDetails.getDescription());
        existing.setStatus(reqDetails.getStatus());
        existing.setPriority(reqDetails.getPriority());
        existing.setStartDate(reqDetails.getStartDate());
        existing.setEndDate(reqDetails.getEndDate());
        existing.setUpdatedAt(LocalDateTime.now());

        return requirementRepository.save(existing);
    }

    @Override
    public void deleteRequirement(Long id) {
        // 1. 清理需求阶段及子任务
        var stages = stageRepository.findByRequirementIdOrderByIdAsc(id);
        for (var stage : stages) {
            subTaskRepository.deleteByStageId(stage.getId());
            discussionRepository.deleteByStageId(stage.getId());
        }
        stageRepository.deleteByRequirementId(id);

        // 2. 级联删除关联的 Wiki 文档
        wikiDocumentRepository.deleteByRequirementId(id);

        // 3. 删除需求本身
        requirementRepository.deleteById(id);
    }
}
