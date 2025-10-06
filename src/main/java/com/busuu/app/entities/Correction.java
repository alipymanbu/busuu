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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "correction")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Correction extends BaseEntity
{
    @Id
    @Column(name = "correction_id")
    private String id;

    @Column(name = "correction_audio_name")
    private String correctionAudioName;

    @Column(name = "correction_audio_url")
    private String correctionAudioUrl;

    @Column(name = "correction_text")
    private String correctionText;

    @Column(name = "comment")
    private String comment;

    @ManyToOne
    @JoinColumn(name = "tagged_user")
    private User taggedUser;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "post_id")
    private Post post;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "correction", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Reaction> reactions = new ArrayList<>();

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "correction", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Correction> corrections = new ArrayList<>();

    //Self relation
    @ManyToOne
    @JoinColumn(name = "parent_correction_id")
    private Correction correction;

}
