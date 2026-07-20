package com.reqflow.service;

import com.reqflow.dto.LoginRequest;
import com.reqflow.entity.User;
import java.util.Map;

public interface UserService {
    void register(User user, String plainPassword);
    Map<String, Object> login(LoginRequest loginRequest);
}