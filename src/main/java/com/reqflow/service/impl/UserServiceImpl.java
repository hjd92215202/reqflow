package com.reqflow.service.impl;

import com.reqflow.dto.LoginRequest;
import com.reqflow.entity.User;
import com.reqflow.repository.UserRepository;
import com.reqflow.service.UserService;
import com.reqflow.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void register(User user, String plainPassword) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        user.setPasswordHash(JwtUtil.hashPassword(plainPassword));
        userRepository.save(user);
    }

    @Override
    public Map<String, Object> login(LoginRequest loginRequest) {
        var user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!JwtUtil.checkPassword(loginRequest.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid username or password");
        }

        var token = JwtUtil.generateToken(user.getId(), user.getUsername());
        var response = new HashMap<String, Object>();
        response.put("token", token);
        response.put("nickname", user.getNickname());
        return response;
    }
}