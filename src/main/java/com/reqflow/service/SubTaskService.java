package com.reqflow.service;

import com.reqflow.dto.SubTaskUpdateRequest;
import com.reqflow.entity.SubTask;
import java.util.List;

public interface SubTaskService {
    List<SubTask> getSubTasksByRequirement(Long requirementId, Long userId);

    SubTask createSubTask(SubTask subTask, Long userId);

    SubTask updateSubTask(Long id, SubTaskUpdateRequest subTaskDetails, Long userId);

    void deleteSubTask(Long id, Long userId);
}
