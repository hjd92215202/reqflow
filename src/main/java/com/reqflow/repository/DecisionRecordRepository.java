package com.reqflow.repository;

import com.reqflow.entity.DecisionRecord;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRecordRepository
        extends JpaRepository<DecisionRecord, Long>, JpaSpecificationExecutor<DecisionRecord> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DecisionRecord d where d.id = :id and d.requirementId = :requirementId")
    Optional<DecisionRecord> findForUpdate(
            @Param("requirementId") Long requirementId, @Param("id") Long id);

    Optional<DecisionRecord> findByIdAndRequirementId(Long id, Long requirementId);
}
