package com.reqflow.repository;

import com.reqflow.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    // 按用户ID查询其所有待办，最新创建的在最前面
    List<Todo> findByUserIdOrderByIdDesc(Long userId);
}