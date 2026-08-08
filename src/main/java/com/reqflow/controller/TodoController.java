package com.reqflow.controller;

import com.reqflow.dto.TodoDTO;
import com.reqflow.entity.Todo;
import com.reqflow.service.TodoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    @Autowired
    private TodoService todoService;

    // 获取当前登录用户的所有待办（包含日常与需求待办）
    @GetMapping
    public ResponseEntity<?> getMyTodos(HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(todoService.getMyTodos(userId));
    }

    // 创建日常个人待办
    @PostMapping
    public ResponseEntity<?> createTodo(@RequestBody Todo todo, HttpServletRequest request) {
        var userId = (Long) request.getAttribute("userId");
        var created = todoService.createPersonalTodo(todo, userId);
        return ResponseEntity.ok(created);
    }

    // 更新待办
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTodo(@PathVariable Long id, @RequestBody TodoDTO dto, HttpServletRequest request) {
        try {
            var userId = (Long) request.getAttribute("userId");
            var updated = todoService.updateTodo(id, dto, userId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 快捷状态切换（打勾/取消打勾）
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleTodo(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") Boolean isProjectTask,
            HttpServletRequest request) {
        try {
            var userId = (Long) request.getAttribute("userId");
            var toggled = todoService.toggleTodoStatus(id, isProjectTask, userId);
            return ResponseEntity.ok(toggled);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 删除待办
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTodo(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") Boolean isProjectTask,
            HttpServletRequest request) {
        try {
            var userId = (Long) request.getAttribute("userId");
            todoService.deleteTodo(id, isProjectTask, userId);
            return ResponseEntity.ok("Delete successful");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}