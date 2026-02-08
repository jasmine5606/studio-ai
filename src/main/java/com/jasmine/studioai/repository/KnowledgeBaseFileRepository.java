package com.jasmine.studioai.repository;

import com.jasmine.studioai.model.KnowledgeBaseFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeBaseFileRepository extends JpaRepository<KnowledgeBaseFile, String> {
    List<KnowledgeBaseFile> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    void deleteByUserIdAndId(String userId, String id);
}
