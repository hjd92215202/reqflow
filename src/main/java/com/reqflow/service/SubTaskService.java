package com.reqflow.service;

import com.reqflow.entity.SubTask;
import java.util.List;

public interface SubTaskService {
    List<SubTask> getSubTasksByRequirement(Long requirementId);
    SubTask createSubTask(SubTask subTask);
    SubTask updateSubTask(Long id, SubTask subTaskDetails);
    void deleteSubTask(Long id);
}