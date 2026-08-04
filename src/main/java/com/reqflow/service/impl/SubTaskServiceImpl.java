package com.reqflow.service.impl;

import com.reqflow.entity.SubTask;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.service.SubTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubTaskServiceImpl implements SubTaskService {

    @Autowired
    private SubTaskRepository subTaskRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SubTask> getSubTasksByRequirement(Long stageId) {
        return subTaskRepository.findByStageIdOrderByIdAsc(stageId);
    }

    @Override
    @Transactional
    public SubTask createSubTask(SubTask subTask) {
        if (subTask.getStatus() == null) subTask.setStatus("TODO");
        return subTaskRepository.save(subTask);
    }

    @Override
    @Transactional
    public SubTask updateSubTask(Long id, SubTask subTaskDetails) {
        var existing = subTaskRepository.findById(id)
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
    public void deleteSubTask(Long id) {
        // 1. 查找所有以当前任务为父节点的子任务
        List<SubTask> children = subTaskRepository.findByParentId(id);

        // 2. 递归深度优先清理子节点（确保整棵子树干净移除）
        for (SubTask child : children) {
            deleteSubTask(child.getId());
        }

        // 3. 删除节点本身
        subTaskRepository.deleteById(id);
    }
}