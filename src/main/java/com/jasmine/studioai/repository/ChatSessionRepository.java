package com.jasmine.studioai.repository;

import com.jasmine.studioai.model.ChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByUserIdOrderByLastActiveAtDesc(String userId, Pageable pageable);
    void deleteByUserIdAndId(String userId, String id);
}
