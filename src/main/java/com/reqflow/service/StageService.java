package com.reqflow.service;

import com.reqflow.entity.Stage;
import java.util.List;

public interface StageService {
    List<Stage> getStagesByRequirement(Long requirementId);
    Stage createStage(Stage stage);
    Stage updateStage(Long id, Stage stageDetails);
    void deleteStage(Long id);
}