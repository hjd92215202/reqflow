package com.reqflow.service.impl;

import com.reqflow.entity.SubTask;
import com.reqflow.repository.SubTaskRepository;
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
        return subTaskRepository.save(subTask);
    }

    @Override
    @Transactional
    public SubTask updateSubTask(Long id, SubTask subTaskDetails, Long userId) {
        requirementAccessService.requireSubTaskOwner(id, userId);
        var existing =
                subTaskRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("SubTask not found"));
        existing.setTitle(subTaskDetails.getTitle());
        existing.setAssignee(subTaskDetails.getAssignee());
        existing.setStatus(subTaskDetails.getStatus());
        existing.setStartDate(subTaskDetails.getStartDate());
        existing.setEndDate(subTaskDetails.getEndDate());
        existing.setCustomFields(subTaskDetails.getCustomFields());
        existing.setUpdatedAt(LocalDateTime.now());
        return subTaskRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteSubTask(Long id, Long userId) {
        requirementAccessService.requireSubTaskOwner(id, userId);
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
