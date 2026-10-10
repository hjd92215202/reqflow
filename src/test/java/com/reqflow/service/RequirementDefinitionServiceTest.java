package com.reqflow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reqflow.dto.RequirementDefinition;
import com.reqflow.dto.RequirementDefinition.SuccessCriterion;
import com.reqflow.entity.Requirement;
import com.reqflow.repository.RequirementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RequirementDefinitionServiceTest {
    private RequirementRepository repository;
    private RequirementAccessService access;
    private ActivityLogService activity;
    private RequirementDefinitionService service;
    private Requirement requirement;

    @BeforeEach
    void setup() {
        repository = mock(RequirementRepository.class);
        access = mock(RequirementAccessService.class);
        activity = mock(ActivityLogService.class);
        service = new RequirementDefinitionService(repository, access, activity);
        requirement = new Requirement();
        requirement.setId(10L);
        requirement.setTitle("旧标题");
        requirement.setDescription("旧背景");
        requirement.setStatus("DONE");
        when(repository.findById(10L)).thenReturn(Optional.of(requirement));
        when(repository.findForDefinitionUpdate(10L)).thenReturn(Optional.of(requirement));
    }

    private RequirementDefinition complete() {
        return new RequirementDefinition(
                "问题",
                "结果",
                List.of(),
                List.of(),
                List.of(),
                List.of(new SuccessCriterion("criterion-1", "可核查标准", "测试", "")));
    }

    @Test
    void emptyLegacyJsonIsNormalizedWithoutBackfill() {
        requirement.setDefinition(new RequirementDefinition(null, null, null, null, null, null));
        var result = service.get(10L, 1L);
        assertThat(result.state()).isEqualTo("NOT_STARTED");
        assertThat(result.definition()).isEqualTo(RequirementDefinition.empty());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void incompleteDraftCanBeSavedButNotConfirmed() {
        var draft = new RequirementDefinition("问题", "", null, null, null, null);
        assertThat(service.save(10L, 1L, 0L, draft).state()).isEqualTo("IN_PROGRESS");
        assertThatThrownBy(() -> service.confirm(10L, 1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        e ->
                                assertThat(((ResponseStatusException) e).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void confirmAndEditInvalidateConfirmationWithoutChangingLegacyFields() {
        service.save(10L, 1L, 0L, complete());
        var confirmed = service.confirm(10L, 1L, 1L);
        assertThat(confirmed.state()).isEqualTo("CONFIRMED");
        assertThat(confirmed.confirmedBy()).isEqualTo(1L);
        assertThat(confirmed.version()).isEqualTo(2L);
        var changed =
                new RequirementDefinition(
                        "新问题", "结果", List.of(), List.of(), List.of(), complete().successCriteria());
        var result = service.save(10L, 1L, 2L, changed);
        assertThat(result.confirmedAt()).isNull();
        assertThat(result.confirmedBy()).isNull();
        assertThat(result.state()).isEqualTo("IN_PROGRESS");
        assertThat(requirement.getTitle()).isEqualTo("旧标题");
        assertThat(requirement.getDescription()).isEqualTo("旧背景");
        assertThat(requirement.getStatus()).isEqualTo("DONE");
        verify(activity).record(10L, 1L, "REQUIREMENT", 10L, "DEFINITION_UPDATE", "修改了问题定义，需要重新确认");
    }

    @Test
    void unchangedSavePreservesConfirmationAndVersion() {
        requirement.setDefinition(complete());
        requirement.setDefinitionConfirmedAt(Instant.now());
        requirement.setDefinitionConfirmedBy(1L);
        requirement.setDefinitionVersion(2L);
        assertThat(service.save(10L, 1L, 2L, complete()).state()).isEqualTo("CONFIRMED");
        assertThat(requirement.getDefinitionVersion()).isEqualTo(2);
        verifyNoInteractions(activity);
    }

    @Test
    void staleSaveAndStaleConfirmationAreRejected() {
        requirement.setDefinitionVersion(2L);
        assertThatThrownBy(() -> service.save(10L, 1L, 1L, complete()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        e ->
                                assertThat(((ResponseStatusException) e).getStatusCode())
                                        .isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.confirm(10L, 1L, 1L))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(activity);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void permissionDeniedPreventsReadSaveAndConfirm() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(access)
                .requireRequirementOwner(10L, 2L);
        assertThatThrownBy(() -> service.get(10L, 2L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.save(10L, 2L, 0L, complete()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.confirm(10L, 2L, 0L))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(activity);
    }

    @Test
    void duplicateCriterionIdsAndOversizedFieldsAreRejected() {
        var duplicate =
                new RequirementDefinition(
                        "问题",
                        "结果",
                        null,
                        null,
                        null,
                        List.of(
                                new SuccessCriterion("same", "一", "", ""),
                                new SuccessCriterion("same", "二", "", "")));
        assertThatThrownBy(() -> service.save(10L, 1L, 0L, duplicate))
                .isInstanceOf(ResponseStatusException.class);
        var oversized = new RequirementDefinition("a".repeat(10001), "", null, null, null, null);
        assertThatThrownBy(() -> service.save(10L, 1L, 0L, oversized))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(activity);
    }

    @Test
    void legacyJsonCannotSupplyDefinitionOrConfirmation() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        Requirement input =
                mapper.readValue(
                        "{\"title\":\"旧客户端\",\"definitionVersion\":99,\"definitionConfirmedBy\":9,\"definition\":{\"problemStatement\":\"伪造\"}}",
                        Requirement.class);
        assertThat(input.getDefinitionVersion()).isZero();
        assertThat(input.getDefinitionConfirmedBy()).isNull();
        assertThat(input.getDefinition()).isEqualTo(RequirementDefinition.empty());
        assertThat(mapper.writeValueAsString(input))
                .doesNotContain("definitionVersion", "definitionConfirmedBy", "problemStatement");
    }
}
