package com.reqflow.service;

import com.reqflow.entity.Stage;
import java.util.List;

public interface StageService {
    List<Stage> getStagesByRequirement(Long requirementId, Long userId);

    Stage createStage(Stage stage, Long userId);

    Stage updateStage(Long id, Stage stageDetails, Long userId);

    void deleteStage(Long id, Long userId);
}
