package com.jasmine.studioai.repository;

import com.jasmine.studioai.model.LiteratureDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LiteratureDocumentRepository extends JpaRepository<LiteratureDocument, String> {
    List<LiteratureDocument> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<LiteratureDocument> findByUserIdAndProjectTag(String userId, String projectTag);
}
