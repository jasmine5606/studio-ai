package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 100)
    private String displayName;

    @Column(length = 200)
    private String email;

    @Column(length = 50)
    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 100)
    private String teamId;

    public enum UserRole {
        ADMIN, USER, VIEWER
    }
}
