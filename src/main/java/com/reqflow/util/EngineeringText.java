package com.reqflow.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class EngineeringText {
    private EngineeringText() {}

    public static String optional(String value) {
        if (value == null) return null;
        if (value.length() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标、产出与完成标准各最多 10000 字");
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
