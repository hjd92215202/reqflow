package com.reqflow.service;

import com.reqflow.entity.TaskDependency;
import com.reqflow.repository.StageRepository;
import com.reqflow.repository.SubTaskRepository;
import com.reqflow.repository.TaskDependencyRepository;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TaskDependencyService {

    @Autowired private TaskDependencyRepository dependencyRepository;

    @Autowired private StageRepository stageRepository;

    @Autowired private SubTaskRepository subTaskRepository;

    @Autowired private RequirementAccessService requirementAccessService;

    @Transactional(readOnly = true)
    public List<TaskDependency> getByStage(Long stageId, Long userId) {
        requirementAccessService.requireStageOwner(stageId, userId);
        return dependencyRepository.findByStageIdOrderByIdAsc(stageId);
    }

    public TaskDependency create(TaskDependency dependency, Long userId) {
        Long stageId = dependency.getStageId();
        requirementAccessService.requireStageOwner(stageId, userId);
        requireTaskInStage(dependency.getPredecessorId(), stageId);
        requireTaskInStage(dependency.getSuccessorId(), stageId);
        if (dependency.getPredecessorId().equals(dependency.getSuccessorId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A task cannot depend on itself");
        }
        if (dependencyRepository.existsByStageIdAndPredecessorIdAndSuccessorId(
                stageId, dependency.getPredecessorId(), dependency.getSuccessorId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dependency already exists");
        }
        if (wouldCreateCycle(stageId, dependency.getPredecessorId(), dependency.getSuccessorId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Dependency would create a cycle");
        }
        if (dependency.getType() == null || dependency.getType().isBlank()) {
            dependency.setType("FINISH_TO_START");
        }
        if (!Set.of("FINISH_TO_START", "START_TO_START", "FINISH_TO_FINISH")
                .contains(dependency.getType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported dependency type");
        }
        dependency.setId(null);
        return dependencyRepository.save(dependency);
    }

    public void delete(Long id, Long userId) {
        TaskDependency dependency =
                dependencyRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Dependency not found"));
        requirementAccessService.requireStageOwner(dependency.getStageId(), userId);
        dependencyRepository.delete(dependency);
    }

    private void requireTaskInStage(Long taskId, Long stageId) {
        boolean belongsToStage =
                taskId != null
                        && stageId != null
                        && subTaskRepository
                                .findById(taskId)
                                .map(task -> stageId.equals(task.getStageId()))
                                .orElse(false);
        if (!belongsToStage) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Both tasks must belong to the selected stage");
        }
    }

    private boolean wouldCreateCycle(Long stageId, Long predecessorId, Long successorId) {
        Map<Long, Set<Long>> outgoing = new HashMap<>();
        for (TaskDependency existing : dependencyRepository.findByStageIdOrderByIdAsc(stageId)) {
            outgoing.computeIfAbsent(existing.getPredecessorId(), ignored -> new HashSet<>())
                    .add(existing.getSuccessorId());
        }

        ArrayDeque<Long> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pending.add(successorId);
        while (!pending.isEmpty()) {
            Long current = pending.removeFirst();
            if (current.equals(predecessorId)) {
                return true;
            }
            if (visited.add(current)) {
                pending.addAll(outgoing.getOrDefault(current, Set.of()));
            }
        }
        return false;
    }
}
