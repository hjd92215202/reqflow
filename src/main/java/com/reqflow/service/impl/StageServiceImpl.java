package com.reqflow.service.impl;

import com.reqflow.entity.Stage;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.repository.DiscussionRepository;
import com.reqflow.service.StageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional // 优化引入：类级别显式声明写事务，确保数据一致性与崩溃回滚
public class StageServiceImpl implements StageService {

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private SubTaskRepository subTaskRepository; // 优化引入：注入子任务仓库用于级联删除

    @Autowired
    private DiscussionRepository discussionRepository; // 优化引入：注入日志仓库用于级联删除

    @Override
    @Transactional(readOnly = true) // 优化引入：只读事务优化，绕过 Hibernate 脏检查，提升读吞吐量
    public List<Stage> getStagesByRequirement(Long requirementId) {
        return stageRepository.findByRequirementIdOrderByIdAsc(requirementId);
    }

    @Override
    public Stage createStage(Stage stage) {
        if (stage.getStatus() == null) stage.setStatus("TODO");
        return stageRepository.save(stage);
    }

    @Override
    public Stage updateStage(Long id, Stage stageDetails) {
        var existing = stageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stage not found"));
        existing.setTitle(stageDetails.getTitle());
        existing.setStartDate(stageDetails.getStartDate());
        existing.setEndDate(stageDetails.getEndDate());
        existing.setStatus(stageDetails.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return stageRepository.save(existing);
    }

    @Override
    public void deleteStage(Long id) {
        // 优化：在物理删除阶段本身之前，先行一键物理删除其关联的所有子任务及日志（防数据孤儿）
        subTaskRepository.deleteByStageId(id);
        discussionRepository.deleteByStageId(id);
        stageRepository.deleteById(id);
    }
}