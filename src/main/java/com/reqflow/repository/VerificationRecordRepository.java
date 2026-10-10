package com.reqflow.repository;

import com.reqflow.entity.VerificationRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface VerificationRecordRepository
        extends JpaRepository<VerificationRecord, Long>,
                JpaSpecificationExecutor<VerificationRecord> {
    Optional<VerificationRecord> findByIdAndRequirementId(Long id, Long requirementId);

    Optional<VerificationRecord> findByRequirementIdAndClientRequestId(
            Long requirementId, String clientRequestId);

    interface Latest {
        Long getId();

        String getSuccessCriterionId();

        Long getSubTaskId();

        String getResultStatus();

        java.time.Instant getCreatedAt();
    }

    @Query(
            """
        select v.id as id, v.successCriterionId as successCriterionId, v.subTaskId as subTaskId,
        v.resultStatus as resultStatus, v.createdAt as createdAt from VerificationRecord v
        where v.requirementId=:requirementId and v.successCriterionId is not null
        and v.invalidatedAt is null and v.voidedAt is null and not exists (
          select n.id from VerificationRecord n where n.requirementId=v.requirementId
          and n.successCriterionId=v.successCriterionId and n.invalidatedAt is null and n.voidedAt is null
          and (n.createdAt>v.createdAt or (n.createdAt=v.createdAt and n.id>v.id)))
        """)
    List<Latest> latestCriteria(Long requirementId);

    @Query(
            """
        select v.id as id, v.successCriterionId as successCriterionId, v.subTaskId as subTaskId,
        v.resultStatus as resultStatus, v.createdAt as createdAt from VerificationRecord v
        where v.requirementId=:requirementId and v.subTaskId is not null
        and v.invalidatedAt is null and v.voidedAt is null and not exists (
          select n.id from VerificationRecord n where n.requirementId=v.requirementId
          and n.subTaskId=v.subTaskId and n.invalidatedAt is null and n.voidedAt is null
          and (n.createdAt>v.createdAt or (n.createdAt=v.createdAt and n.id>v.id)))
        """)
    List<Latest> latestTasks(Long requirementId);
}
