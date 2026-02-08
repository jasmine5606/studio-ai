package com.jasmine.studioai.repository;

import com.jasmine.studioai.model.ExperimentRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentRecordRepository extends JpaRepository<ExperimentRecord, String> {
    List<ExperimentRecord> findByProjectIdOrderByCreatedAtDesc(String projectId, Pageable pageable);

    @Query("SELECT e FROM ExperimentRecord e WHERE e.projectId = :projectId AND " +
           "(LOWER(e.conclusion) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.conditions) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ExperimentRecord> searchByProject(@Param("projectId") String projectId, @Param("keyword") String keyword);
}
