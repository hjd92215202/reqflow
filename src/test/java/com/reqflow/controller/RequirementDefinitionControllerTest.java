package com.reqflow.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.reqflow.dto.DefinitionResponse;
import com.reqflow.dto.RequirementDefinition;
import com.reqflow.interceptor.JwtInterceptor;
import com.reqflow.service.RequirementDefinitionService;
import com.reqflow.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class RequirementDefinitionControllerTest {
    private RequirementDefinitionService service;
    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setup() {
        service = mock(RequirementDefinitionService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new RequirementDefinitionController(service))
                        .addInterceptors(new JwtInterceptor())
                        .build();
        token = "Bearer " + JwtUtil.generateToken(1L, "owner");
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/requirements/10/definition")).andExpect(status().isUnauthorized());
        mvc.perform(
                        put("/api/requirements/10/definition")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/requirements/10/definition/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void responseMatchesContractAndErrorsRetainStatus() throws Exception {
        when(service.get(10L, 1L))
                .thenReturn(
                        new DefinitionResponse(
                                RequirementDefinition.empty(), "NOT_STARTED", 0, null, null));
        mvc.perform(get("/api/requirements/10/definition").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.definition.successCriteria").isArray());
        when(service.confirm(10L, 1L, 0L))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "定义已更新"));
        mvc.perform(
                        post("/api/requirements/10/definition/confirm")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("定义已更新"));
    }

    @Test
    void malformedRequestIsBadRequest() throws Exception {
        mvc.perform(
                        put("/api/requirements/10/definition")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
