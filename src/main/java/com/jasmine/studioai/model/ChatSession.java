package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_session_user", columnList = "userId"),
        @Index(name = "idx_session_active", columnList = "userId,lastActiveAt")
})
@Getter
@Setter
@NoArgsConstructor
public class ChatSession extends BaseEntity {

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(length = 200)
    private String title;

    @Column(length = 5000)
    private String summary;

    @Column(nullable = false)
    private long messageCount;

    private long lastActiveAt;

    @Column(nullable = false)
    private boolean pinned;
}
