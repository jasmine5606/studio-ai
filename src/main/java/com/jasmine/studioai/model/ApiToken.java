package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "api_tokens", indexes = {
        @Index(name = "idx_token_user", columnList = "userId"),
        @Index(name = "idx_token_value", columnList = "tokenHash")
})
@Getter
@Setter
@NoArgsConstructor
public class ApiToken extends BaseEntity {

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 200)
    private String tokenHash;

    @Column(length = 100)
    private String name;

    private Instant expiresAt;

    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean revoked;
}
