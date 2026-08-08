package com.reqflow.service;

import com.reqflow.dto.TodoDTO;
import com.reqflow.entity.Todo;
import java.util.List;

public interface TodoService {
    List<TodoDTO> getMyTodos(Long userId);
    Todo createPersonalTodo(Todo todo, Long userId);
    TodoDTO updateTodo(Long id, TodoDTO dto, Long userId);
    TodoDTO toggleTodoStatus(Long id, Boolean isProjectTask, Long userId);
    void deleteTodo(Long id, Boolean isProjectTask, Long userId);
}