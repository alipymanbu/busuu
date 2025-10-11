package com.busuu.app.entities;

import com.busuu.app.entities.progresses.LessonProgress;
import com.busuu.app.entities.questions.Question;
import com.busuu.app.entities.topics.Topic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "lesson")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
//@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Lesson extends BaseEntity{

    @Id
    @Column(name = "lesson_id")
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "lesson_order")
    private Integer lessonOrder;

    @Column(name = "flag_icon_url")
    private String flagIconUrl;

    @Column(name = "flag_icon_name")
    private String flagIconName;

    @ManyToOne
    @JoinColumn(name = "chapter_id", nullable = true)
    private Chapter chapter;

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Word> words = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GrammarSection> grammarSections = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LessonProgress> lessonProgresses = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Topic> topics = new ArrayList<>();
}
