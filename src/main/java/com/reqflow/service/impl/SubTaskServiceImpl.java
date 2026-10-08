package com.reqflow.service.impl;

import com.reqflow.entity.SubTask;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.service.ActivityLogService;
import com.reqflow.service.RequirementAccessService;
import com.reqflow.service.SubTaskService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubTaskServiceImpl implements SubTaskService {

    @Autowired private SubTaskRepository subTaskRepository;

    @Autowired private RequirementAccessService requirementAccessService;

    @Autowired private ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public List<SubTask> getSubTasksByRequirement(Long stageId, Long userId) {
        requirementAccessService.requireStageOwner(stageId, userId);
        return subTaskRepository.findByStageIdOrderByIdAsc(stageId);
    }

    @Override
    @Transactional
    public SubTask createSubTask(SubTask subTask, Long userId) {
        requirementAccessService.requireStageOwner(subTask.getStageId(), userId);
        requirementAccessService.requireParentTaskInStage(
                subTask.getParentId(), subTask.getStageId());
        if (subTask.getStatus() == null) subTask.setStatus("TODO");
        SubTask saved = subTaskRepository.save(subTask);
        Long requirementId = activityLogService.resolveRequirementIdForStage(saved.getStageId());
        activityLogService.record(
                requirementId,
                userId,
                "SUB_TASK",
                saved.getId(),
                "CREATE",
                "创建了工作项「" + saved.getTitle() + "」");
        return saved;
    }

    @Override
    @Transactional
    public SubTask updateSubTask(Long id, SubTask subTaskDetails, Long userId) {
        requirementAccessService.requireSubTaskOwner(id, userId);
        var existing =
                subTaskRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("SubTask not found"));
        boolean statusChanged =
                !java.util.Objects.equals(existing.getStatus(), subTaskDetails.getStatus());
        existing.setTitle(subTaskDetails.getTitle());
        existing.setAssignee(subTaskDetails.getAssignee());
        existing.setStatus(subTaskDetails.getStatus());
        existing.setStartDate(subTaskDetails.getStartDate());
        existing.setEndDate(subTaskDetails.getEndDate());
        // Preserve notes when older clients update a task without sending this field.
        if (subTaskDetails.getNote() != null) {
            existing.setNote(subTaskDetails.getNote());
        }
        existing.setCustomFields(subTaskDetails.getCustomFields());
        existing.setUpdatedAt(LocalDateTime.now());
        SubTask saved = subTaskRepository.save(existing);
        Long requirementId = activityLogService.resolveRequirementIdForTask(id);
        activityLogService.record(
                requirementId,
                userId,
                "SUB_TASK",
                id,
                statusChanged ? "STATUS" : "UPDATE",
                "更新了工作项「" + saved.getTitle() + "」");
        return saved;
    }

    @Override
    @Transactional
    public void deleteSubTask(Long id, Long userId) {
        requirementAccessService.requireSubTaskOwner(id, userId);
        SubTask task =
                subTaskRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("SubTask not found"));
        Long requirementId = activityLogService.resolveRequirementIdForTask(id);
        activityLogService.record(
                requirementId, userId, "SUB_TASK", id, "DELETE", "删除了工作项「" + task.getTitle() + "」");
        // 1. 查找所有以当前任务为父节点的子任务
        List<SubTask> children = subTaskRepository.findByParentId(id);

        // 2. 递归深度优先清理子节点（确保整棵子树干净移除）
        for (SubTask child : children) {
            deleteSubTask(child.getId(), userId);
        }

        // 3. 删除节点本身
        subTaskRepository.deleteById(id);
    }
}
