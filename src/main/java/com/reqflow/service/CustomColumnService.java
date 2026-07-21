package com.reqflow.service;

import com.reqflow.entity.CustomColumn;
import java.util.List;

public interface CustomColumnService {
    List<CustomColumn> getColumnsByRequirement(Long requirementId);
    CustomColumn createColumn(CustomColumn column);
    void deleteColumn(Long id);
}