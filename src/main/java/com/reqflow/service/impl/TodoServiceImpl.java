package com.reqflow.service.impl;

import com.reqflow.dto.TodoDTO;
import com.reqflow.entity.Requirement;
import com.reqflow.entity.Stage;
import com.reqflow.entity.SubTask;
import com.reqflow.entity.Todo;
import com.reqflow.repository.*;
import com.reqflow.service.TodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class TodoServiceImpl implements TodoService {

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubTaskRepository subTaskRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TodoDTO> getMyTodos(Long userId) {
        List<TodoDTO> resultList = new ArrayList<>();

        // 1. 获取用户个人创建的私有待办
        List<Todo> personalTodos = todoRepository.findByUserIdOrderByIdDesc(userId);
        for (Todo t : personalTodos) {
            TodoDTO dto = new TodoDTO();
            dto.setId(t.getId());
            dto.setUserId(t.getUserId());
            dto.setTitle(t.getTitle());
            dto.setStatus(t.getStatus());
            dto.setPriority(t.getPriority());
            dto.setDueDate(t.getDueDate());
            dto.setIsProjectTask(false);
            dto.setSubTaskId(t.getSubTaskId());
            dto.setCreatedAt(t.getCreatedAt());
            dto.setUpdatedAt(t.getUpdatedAt());
            resultList.add(dto);
        }

        // 2. 获取指派给当前用户的需求矩阵子任务（按昵称或用户名匹配）
        var userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            String nickname = user.getNickname();
            String username = user.getUsername();

            List<SubTask> assignedSubTasks = subTaskRepository.findAll().stream()
                    .filter(st -> st.getAssignee() != null &&
                            (st.getAssignee().trim().equalsIgnoreCase(nickname != null ? nickname.trim() : "") ||
                                    st.getAssignee().trim().equalsIgnoreCase(username != null ? username.trim() : "")))
                    .collect(Collectors.toList());

            if (!assignedSubTasks.isEmpty()) {
                // 预加载所有 Stages 与 Requirements 避免 N+1
                Map<Long, Stage> stageMap = stageRepository.findAll().stream()
                        .collect(Collectors.toMap(Stage::getId, Function.identity(), (a, b) -> a));
                Map<Long, Requirement> reqMap = requirementRepository.findAll().stream()
                        .collect(Collectors.toMap(Requirement::getId, Function.identity(), (a, b) -> a));

                for (SubTask st : assignedSubTasks) {
                    TodoDTO dto = new TodoDTO();
                    dto.setId(st.getId());
                    dto.setUserId(userId);
                    dto.setTitle(st.getTitle());
                    dto.setStatus(st.getStatus());
                    dto.setPriority("MEDIUM");
                    dto.setDueDate(st.getEndDate());
                    dto.setIsProjectTask(true);
                    dto.setSubTaskId(st.getId());

                    Stage stage = stageMap.get(st.getStageId());
                    if (stage != null) {
                        dto.setStageId(stage.getId());
                        dto.setStageTitle(stage.getTitle());
                        Requirement req = reqMap.get(stage.getRequirementId());
                        if (req != null) {
                            dto.setRequirementId(req.getId());
                            dto.setRequirementTitle(req.getTitle());
                        }
                    }

                    dto.setCreatedAt(st.getCreatedAt());
                    dto.setUpdatedAt(st.getUpdatedAt());
                    resultList.add(dto);
                }
            }
        }

        // 按 ID 降序排列
        resultList.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return resultList;
    }

    @Override
    public Todo createPersonalTodo(Todo todo, Long userId) {
        todo.setUserId(userId);
        if (todo.getStatus() == null) todo.setStatus("TODO");
        if (todo.getPriority() == null) todo.setPriority("MEDIUM");
        return todoRepository.save(todo);
    }

    @Override
    public TodoDTO updateTodo(Long id, TodoDTO dto, Long userId) {
        if (Boolean.TRUE.equals(dto.getIsProjectTask())) {
            // 更新矩阵子任务
            SubTask subTask = subTaskRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("SubTask not found"));
            subTask.setTitle(dto.getTitle());
            subTask.setStatus(dto.getStatus());
            subTask.setEndDate(dto.getDueDate());
            subTask.setUpdatedAt(LocalDateTime.now());
            subTaskRepository.save(subTask);
        } else {
            // 更新个人私有待办
            Todo existing = todoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Todo not found"));
            if (!existing.getUserId().equals(userId)) {
                throw new RuntimeException("Permission denied");
            }
            existing.setTitle(dto.getTitle());
            existing.setStatus(dto.getStatus());
            existing.setPriority(dto.getPriority());
            existing.setDueDate(dto.getDueDate());
            existing.setUpdatedAt(LocalDateTime.now());
            todoRepository.save(existing);
        }
        return dto;
    }

    @Override
    public TodoDTO toggleTodoStatus(Long id, Boolean isProjectTask, Long userId) {
        TodoDTO dto = new TodoDTO();
        dto.setId(id);
        dto.setIsProjectTask(isProjectTask);

        if (Boolean.TRUE.equals(isProjectTask)) {
            SubTask subTask = subTaskRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("SubTask not found"));
            String newStatus = "DONE".equals(subTask.getStatus()) ? "TODO" : "DONE";
            subTask.setStatus(newStatus);
            subTask.setUpdatedAt(LocalDateTime.now());
            subTaskRepository.save(subTask);
            dto.setStatus(newStatus);
        } else {
            Todo existing = todoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Todo not found"));
            if (!existing.getUserId().equals(userId)) {
                throw new RuntimeException("Permission denied");
            }
            String newStatus = "DONE".equals(existing.getStatus()) ? "TODO" : "DONE";
            existing.setStatus(newStatus);
            existing.setUpdatedAt(LocalDateTime.now());
            todoRepository.save(existing);
            dto.setStatus(newStatus);
        }
        return dto;
    }

    @Override
    public void deleteTodo(Long id, Boolean isProjectTask, Long userId) {
        if (Boolean.TRUE.equals(isProjectTask)) {
            // 需求待办解绑或删除子任务
            subTaskRepository.deleteById(id);
        } else {
            Todo existing = todoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Todo not found"));
            if (!existing.getUserId().equals(userId)) {
                throw new RuntimeException("Permission denied");
            }
            todoRepository.deleteById(id);
        }
    }
}