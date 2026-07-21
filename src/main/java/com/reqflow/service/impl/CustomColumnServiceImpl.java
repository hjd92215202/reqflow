package com.reqflow.service.impl;

import com.reqflow.entity.CustomColumn;
import com.reqflow.repository.CustomColumnRepository;
import com.reqflow.service.CustomColumnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CustomColumnServiceImpl implements CustomColumnService {

    @Autowired
    private CustomColumnRepository customColumnRepository;

    @Override
    public List<CustomColumn> getColumnsByRequirement(Long requirementId) {
        return customColumnRepository.findByRequirementId(requirementId);
    }

    @Override
    public CustomColumn createColumn(CustomColumn column) {
        return customColumnRepository.save(column);
    }

    @Override
    public void deleteColumn(Long id) {
        customColumnRepository.deleteById(id);
    }
}