package com.reqflow.service.impl;

import com.reqflow.entity.SubTask;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.service.SubTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubTaskServiceImpl implements SubTaskService {

    @Autowired
    private SubTaskRepository subTaskRepository;

    @Override
    public List<SubTask> getSubTasksByRequirement(Long stageId) {
        return subTaskRepository.findByStageIdOrderByIdAsc(stageId); // 更改为按阶段ID查询
    }

    @Override
    public SubTask createSubTask(SubTask subTask) {
        subTask.setIsCompleted(false);
        return subTaskRepository.save(subTask);
    }

    @Override
    public SubTask updateSubTask(Long id, SubTask subTaskDetails) {
        var existing = subTaskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SubTask not found"));
        existing.setTitle(subTaskDetails.getTitle());
        existing.setAssignee(subTaskDetails.getAssignee());
        existing.setIsCompleted(subTaskDetails.getIsCompleted());
        existing.setUpdatedAt(LocalDateTime.now());
        return subTaskRepository.save(existing);
    }

    @Override
    public void deleteSubTask(Long id) {
        subTaskRepository.deleteById(id);
    }
}