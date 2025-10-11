package com.busuu.app.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.List;

@Entity
@Table(name = "word")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
//@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Word extends BaseEntity {

    @Id
    @Column(name = "word_id")
    private String id;

    @Column(name = "language_code", nullable = false)
    private String languageCode;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "translation")
    private String translation;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "audio_name")
    private String audioName;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_name")
    private String imageName;

    @ManyToOne
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @JsonIgnore
    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<WordExample> examples;

    @JsonIgnore
    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<UserWord> userWords;
}
