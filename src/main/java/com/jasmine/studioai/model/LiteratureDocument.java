package com.jasmine.studioai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "literature_docs", indexes = {
        @Index(name = "idx_lit_user", columnList = "userId"),
        @Index(name = "idx_lit_tag", columnList = "projectTag")
})
@Getter
@Setter
@NoArgsConstructor
public class LiteratureDocument extends BaseEntity {

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 50)
    @Enumerated(EnumType.STRING)
    private LiteratureSource source;

    @Column(length = 200)
    private String identifier;

    @Column(length = 100)
    private String projectTag;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(length = 500)
    private String storagePath;

    @Column(length = 5000)
    private String abstractText;

    @Column(length = 500)
    private String authors;

    @Column(name = "pub_year")
    private Integer year;

    @Column(length = 50)
    private String teamId;

    public enum LiteratureSource {
        PDF, ARXIV, DOI
    }
}
