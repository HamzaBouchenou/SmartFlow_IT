package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.CommentMention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** §6.4/ADR-24 - mentions retenues sur les commentaires d'une demande. */
public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {

    List<CommentMention> findByCommentIdIn(List<Long> commentIds);
}
