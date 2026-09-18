package com.smartflow.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * §6.4/ADR-24 - une personne effectivement mentionnée dans un commentaire. Une ligne n'est
 * écrite que pour quelqu'un qui pouvait déjà lire la demande (canView, RG-06) : cette table
 * n'est donc jamais la trace d'une tentative refusée, seulement d'une mention retenue.
 */
@Entity
@Table(name = "comment_mentions")
public class CommentMention extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentioned_user_id", nullable = false)
    private User mentionedUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    protected CommentMention() {
    }

    public CommentMention(Comment comment, User mentionedUser) {
        this.comment = comment;
        this.mentionedUser = mentionedUser;
    }

    public Comment getComment() {
        return comment;
    }

    public User getMentionedUser() {
        return mentionedUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
