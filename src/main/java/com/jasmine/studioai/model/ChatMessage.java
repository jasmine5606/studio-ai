package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_msg_session", columnList = "sessionId"),
        @Index(name = "idx_msg_session_time", columnList = "sessionId,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage extends BaseEntity {

    @Column(nullable = false, length = 36)
    private String sessionId;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private Integer tokenCount;

    public enum MessageRole {
        USER, ASSISTANT, SYSTEM, TOOL
    }
}
