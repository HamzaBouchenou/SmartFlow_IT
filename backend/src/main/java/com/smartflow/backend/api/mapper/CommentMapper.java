package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.CommentResponse;
import com.smartflow.backend.domain.entity.Comment;
import com.smartflow.backend.domain.entity.User;

public final class CommentMapper {

    private CommentMapper() {
    }

    public static CommentResponse toResponse(Comment comment) {
        User author = comment.getAuthor();
        return new CommentResponse(comment.getId(), author.getId(),
                author.getFirstName() + " " + author.getLastName(), comment.getBody(), comment.getCreatedAt());
    }
}
