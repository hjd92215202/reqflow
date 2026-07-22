package com.reqflow.service.impl;

import com.reqflow.entity.Requirement;
import com.reqflow.repository.RequirementRepository;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.repository.DiscussionRepository;
import com.reqflow.service.RequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional // 优化引入：类级别显式声明写事务，确保主子表级联回滚
public class RequirementServiceImpl implements RequirementService {

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private StageRepository stageRepository; // 优化引入：注入阶段仓库用于多级级联删除

    @Autowired
    private SubTaskRepository subTaskRepository; // 优化引入：注入子任务仓库用于多级级联删除

    @Autowired
    private DiscussionRepository discussionRepository; // 优化引入：注入日志仓库用于多级级联删除

    @Override
    @Transactional(readOnly = true) // 优化引入：只读事务优化，提升查询并发处理能力
    public List<Requirement> getRequirementsByCreator(Long creatorId) {
        return requirementRepository.findByCreatorIdOrderByIdDesc(creatorId);
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
        var existing = requirementRepository.findById(id)
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
        // 核心级联删除逻辑优化：
        // 1. 获取该需求下关联的所有阶段列表
        var stages = stageRepository.findByRequirementIdOrderByIdAsc(id);

        // 2. 深度遍历，彻底清空每一个阶段下的“子任务”和“讨论日志”
        for (var stage : stages) {
            subTaskRepository.deleteByStageId(stage.getId());
            discussionRepository.deleteByStageId(stage.getId());
        }

        // 3. 清空需求下的所有“阶段”数据
        stageRepository.deleteByRequirementId(id);

        // 4. 最后安全删除主需求数据，全过程在一个事务中完成，异常时自动回滚
        requirementRepository.deleteById(id);
    }
}