package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.CommentResponse;
import com.smartflow.backend.domain.entity.Comment;
import com.smartflow.backend.domain.entity.User;

import java.util.List;
import java.util.Map;

public final class CommentMapper {

    private CommentMapper() {
    }

    public static CommentResponse toResponse(Comment comment) {
        return toResponse(comment, List.of());
    }

    public static CommentResponse toResponse(Comment comment, List<User> mentions) {
        User author = comment.getAuthor();
        return new CommentResponse(comment.getId(), author.getId(),
                author.getFirstName() + " " + author.getLastName(), comment.getBody(), comment.getCreatedAt(),
                mentions.stream()
                        .map(user -> new CommentResponse.MentionResponse(user.getId(),
                                user.getFirstName() + " " + user.getLastName()))
                        .toList());
    }

    public static List<CommentResponse> toResponses(List<Comment> comments, Map<Long, List<User>> mentionsByComment) {
        return comments.stream()
                .map(comment -> toResponse(comment, mentionsByComment.getOrDefault(comment.getId(), List.of())))
                .toList();
    }
}
