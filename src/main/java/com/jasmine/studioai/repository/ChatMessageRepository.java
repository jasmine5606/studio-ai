package com.jasmine.studioai.repository;

import com.jasmine.studioai.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
    long countBySessionId(String sessionId);
    void deleteBySessionId(String sessionId);
}
