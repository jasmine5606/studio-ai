package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "experiment_records", indexes = {
        @Index(name = "idx_exp_proj", columnList = "projectId"),
        @Index(name = "idx_exp_user", columnList = "userId")
})
@Getter
@Setter
@NoArgsConstructor
public class ExperimentRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projectId", nullable = false)
    private ExperimentProject project;

    @Column(nullable = false, length = 36, insertable = false, updatable = false)
    private String projectId;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Column(columnDefinition = "TEXT")
    private String conditionsStructured;

    @Column(length = 500)
    private String dataPath;

    @Column(columnDefinition = "TEXT")
    private String conclusion;

    @Column(length = 1000)
    private String failureReason;

    @Column(length = 2000)
    private String tags;

    @Column(length = 50)
    private String teamId;
}
