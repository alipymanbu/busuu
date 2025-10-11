package com.busuu.app.entities;

import com.busuu.app.entities.progresses.ChapterProgress;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chapter")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
//@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Chapter extends BaseEntity {

    @Id
    @Column(name = "chapter_id")
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

//    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
//    private String description;

    @Column(name = "chapter_order")
    private Integer chapterOrder;

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = true)
    private Course course;

    @ManyToOne
    @JoinColumn(name = "level_id", nullable = true)
    private Level level;

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Lesson> lessons = new ArrayList<>();

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChapterProgress> chapterProgresses = new ArrayList<>();
}
