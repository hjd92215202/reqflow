package com.reqflow.service.impl;

import com.reqflow.entity.Stage;
import com.reqflow.repository.StageRepository;
import com.reqflow.service.StageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StageServiceImpl implements StageService {

    @Autowired
    private StageRepository stageRepository;

    @Override
    public List<Stage> getStagesByRequirement(Long requirementId) {
        return stageRepository.findByRequirementIdOrderByIdAsc(requirementId);
    }

    @Override
    public Stage createStage(Stage stage) {
        if (stage.getStatus() == null) stage.setStatus("TODO");
        return stageRepository.save(stage);
    }

    @Override
    public Stage updateStage(Long id, Stage stageDetails) {
        var existing = stageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stage not found"));
        existing.setTitle(stageDetails.getTitle());
        existing.setStartDate(stageDetails.getStartDate());
        existing.setEndDate(stageDetails.getEndDate());
        existing.setStatus(stageDetails.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return stageRepository.save(existing);
    }

    @Override
    public void deleteStage(Long id) {
        stageRepository.deleteById(id);
    }
}