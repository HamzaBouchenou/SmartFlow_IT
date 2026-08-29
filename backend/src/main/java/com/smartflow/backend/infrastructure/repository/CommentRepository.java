package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // §6.4 - "Ajout de commentaires" ; §9.4 l'écran détail les affiche par ordre chronologique.
    List<Comment> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}
