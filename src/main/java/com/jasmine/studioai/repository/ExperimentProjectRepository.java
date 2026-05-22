package com.jasmine.studioai.repository;

import com.jasmine.studioai.model.ExperimentProject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentProjectRepository extends JpaRepository<ExperimentProject, String> {
    List<ExperimentProject> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
