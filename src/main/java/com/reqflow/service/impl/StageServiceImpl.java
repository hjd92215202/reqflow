package com.reqflow.service.impl;

import com.reqflow.dto.StageUpdateRequest;
import com.reqflow.entity.Stage;
import com.reqflow.repository.DiscussionRepository;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.service.ActivityLogService;
import com.reqflow.service.RequirementAccessService;
import com.reqflow.service.StageService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional // 优化引入：类级别显式声明写事务，确保数据一致性与崩溃回滚
public class StageServiceImpl implements StageService {

    @Autowired private StageRepository stageRepository;

    @Autowired private SubTaskRepository subTaskRepository; // 优化引入：注入子任务仓库用于级联删除

    @Autowired private DiscussionRepository discussionRepository; // 优化引入：注入日志仓库用于级联删除

    @Autowired private RequirementAccessService requirementAccessService;

    @Autowired private ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true) // 优化引入：只读事务优化，绕过 Hibernate 脏检查，提升读吞吐量
    public List<Stage> getStagesByRequirement(Long requirementId, Long userId) {
        requirementAccessService.requireRequirementOwner(requirementId, userId);
        return stageRepository.findByRequirementIdOrderByIdAsc(requirementId);
    }

    @Override
    public Stage createStage(Stage stage, Long userId) {
        requirementAccessService.requireRequirementOwner(stage.getRequirementId(), userId);
        if (stage.getStatus() == null) stage.setStatus("TODO");
        Stage saved = stageRepository.save(stage);
        activityLogService.record(
                saved.getRequirementId(),
                userId,
                "STAGE",
                saved.getId(),
                "CREATE",
                "创建了阶段「" + saved.getTitle() + "」");
        return saved;
    }

    @Override
    public Stage updateStage(Long id, StageUpdateRequest stageDetails, Long userId) {
        requirementAccessService.requireStageOwner(id, userId);
        var existing =
                stageRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Stage not found"));
        boolean statusChanged =
                !java.util.Objects.equals(existing.getStatus(), stageDetails.getStatus());
        // Update requests may contain only the changed fields (for example, status).
        // Keep required and unrelated values when they are omitted from the payload.
        if (stageDetails.getTitle() != null) existing.setTitle(stageDetails.getTitle());
        if (stageDetails.isStartDateProvided()) existing.setStartDate(stageDetails.getStartDate());
        if (stageDetails.isEndDateProvided()) existing.setEndDate(stageDetails.getEndDate());
        if (stageDetails.getStatus() != null) existing.setStatus(stageDetails.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        Stage saved = stageRepository.save(existing);
        activityLogService.record(
                saved.getRequirementId(),
                userId,
                "STAGE",
                id,
                statusChanged ? "STATUS" : "UPDATE",
                "更新了阶段「" + saved.getTitle() + "」");
        return saved;
    }

    @Override
    public void deleteStage(Long id, Long userId) {
        requirementAccessService.requireStageOwner(id, userId);
        Stage stage =
                stageRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Stage not found"));
        activityLogService.record(
                stage.getRequirementId(),
                userId,
                "STAGE",
                id,
                "DELETE",
                "删除了阶段「" + stage.getTitle() + "」");
        // 优化：在物理删除阶段本身之前，先行一键物理删除其关联的所有子任务及日志（防数据孤儿）
        subTaskRepository.deleteByStageId(id);
        discussionRepository.deleteByStageId(id);
        stageRepository.deleteById(id);
    }
}
