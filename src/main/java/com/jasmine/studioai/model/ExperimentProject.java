package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "experiment_projects", indexes = {
        @Index(name = "idx_exp_proj_user", columnList = "userId")
})
@Getter
@Setter
@NoArgsConstructor
public class ExperimentProject extends BaseEntity {

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 200)
    private String projectName;

    @Column(length = 1000)
    private String description;

    @Column(length = 50)
    private String teamId;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<ExperimentRecord> records = new ArrayList<>();
}
