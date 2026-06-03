package com.soi.backend.domain.post.service;

import com.soi.backend.domain.category.entity.CategoryUser;
import com.soi.backend.domain.category.repository.CategoryRepository;
import com.soi.backend.domain.category.repository.CategoryUserRepository;
import com.soi.backend.domain.category.service.CategorySetService;
import com.soi.backend.domain.comment.service.CommentService;
import com.soi.backend.domain.friend.entity.Friend;
import com.soi.backend.domain.friend.entity.FriendStatus;
import com.soi.backend.domain.friend.repository.FriendRepository;
import com.soi.backend.domain.media.service.MediaService;
import com.soi.backend.domain.notification.entity.NotificationType;
import com.soi.backend.domain.notification.service.NotificationService;
import com.soi.backend.domain.post.dto.PostCreateReqDto;
import com.soi.backend.domain.post.dto.PostRespDto;
import com.soi.backend.domain.post.dto.PostUpdateReqDto;
import com.soi.backend.domain.post.entity.Post;
import com.soi.backend.domain.post.entity.PostStatus;
import com.soi.backend.domain.post.entity.PostType;
import com.soi.backend.domain.post.repository.PostRepository;
import com.soi.backend.domain.user.entity.User;
import com.soi.backend.domain.user.repository.UserRepository;
import com.soi.backend.global.exception.CustomException;
import com.soi.backend.global.metrics.BusinessMetricsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class PostService {

    private final MediaService mediaService;
    private final PostRepository postRepository;
    private final CategorySetService categorySetService;
    private final CategoryRepository categoryRepository;
    private final CategoryUserRepository categoryUserRepository;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CommentService commentService;
    private final BusinessMetricsService businessMetricsService;

    @Transactional
    public Boolean addPostToCategory(PostCreateReqDto postCreateReqDto) {

        for (int i=0; i<postCreateReqDto.getCategoryId().size(); i++) {
            Long categoryId = postCreateReqDto.getCategoryId().get(i);
            String fileKey = postCreateReqDto.getPostFileKey().get(i);
            String thumbnailImageKey = postCreateReqDto.getThumbnailFileKey().get(i);
            String audioFileKey = "";
            if (postCreateReqDto.getPostFileKey().size() == postCreateReqDto.getAudioFileKey().size()) {
                audioFileKey = postCreateReqDto.getAudioFileKey().get(i);
            }

            Long postId = createPost(postCreateReqDto, categoryId, fileKey, audioFileKey, thumbnailImageKey);

            List<CategoryUser> categoryUsers =
                    categoryUserRepository.findAllByCategoryIdExceptUser(categoryId, postCreateReqDto.getUserId());

            CategoryUser categoryUser = categoryUserRepository.findByCategoryIdAndUserId(categoryId, postCreateReqDto.getUserId())
                    .orElseThrow(() -> new CustomException("카테고리를 찾을 수 없음", HttpStatus.NOT_FOUND));

            categoryUser.setLastViewedAt();

            String categoryName = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new CustomException("카테고리를 찾을 수 없음",HttpStatus.NOT_FOUND))
                    .getName();

            categorySetService.setLastUploadedAndProfile(categoryId, postCreateReqDto.getUserId(), fileKey);

            for (CategoryUser receivers : categoryUsers) {
                Long receiverId = receivers.getUserId();

                if (receivers.getIsAlert()) {
                    notificationService.sendCategoryPostNotification(
                            postCreateReqDto.getUserId(),
                            receiverId,
                            postId,
                            categoryId,
                            notificationService.makeMessage(postCreateReqDto.getUserId(), categoryName, NotificationType.PHOTO_ADDED),
                            fileKey
                    );
                }
            }
        }
        return true;
    }

    @Transactional
    public Long createPost(PostCreateReqDto postCreateReqDto, Long categoryId, String fileKey, String audioFileKey, String thumbnailImageKey) {
        Post post = new Post(
                postCreateReqDto.getUserId(),
                postCreateReqDto.getContent(),
                fileKey,
                audioFileKey,
                thumbnailImageKey,
                categoryId,
                postCreateReqDto.getWaveformData(),
                postCreateReqDto.getDuration(),
                postCreateReqDto.getIsFromGallery(),
                postCreateReqDto.getSavedAspectRatio(),
                postCreateReqDto.getPostType()
        );

        postRepository.save(post);
        businessMetricsService.increment("post_created", "post_type", post.getPostType().name());

        return post.getId();
    }

    @Transactional
    public void updatePost(PostUpdateReqDto postUpdateReqDto) {
        Post originalPost = postRepository.findById(postUpdateReqDto.getPostId())
                .orElseThrow(() -> new CustomException(postUpdateReqDto.getPostId() + " id의 게시물을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        originalPost.update(
                postUpdateReqDto.getContent(),
                postUpdateReqDto.getPostFileKey(),
                postUpdateReqDto.getAudioFileKey(),
                postUpdateReqDto.getWaveformData(),
                postUpdateReqDto.getDuration(),
                postUpdateReqDto.getIsFromGallery(),
                postUpdateReqDto.getSavedAspectRatio(),
                postUpdateReqDto.getPostType()
        );

        postRepository.save(originalPost);
    }

    @Transactional
    public PostStatus setPostStatus(Long postId, PostStatus postStatus) {
        Post originalPost = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(postId + " id의 게시물을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        originalPost.setStatus(postStatus, postStatus ==  PostStatus.ACTIVE);

        postRepository.save(originalPost);

        if (postStatus == PostStatus.DELETED) {
            categorySetService.setCategoryProfile(originalPost);
        }
        return originalPost.getStatus();
    }

    @Transactional
    public void hardDelete(Post post) {
        // 게시물과 관련된 알림 삭제
        notificationService.deletePostNotification(post.getUserId(), post.getId());
        // 카테고리에 등록된 모든 post 삭제
        commentService.deleteComments(post.getId());

        // s3파일 삭제
        if (post.getFileKey() != null && !post.getFileKey().isEmpty()) {
            mediaService.removeMedia(post.getFileKey());
        }
        if (post.getAudioKey() != null && !post.getAudioKey().isEmpty()) {
            mediaService.removeMedia(post.getAudioKey());
        }

        postRepository.deleteById(post.getId());
    }

    @Transactional
    public void hardDeletePosts(Long categoryId) {
        // 카테고리에 등록된 모든 post를 찾음
        List<Post> posts = postRepository.findAllByCategoryId(categoryId);

        for (Post post : posts) {
            hardDelete(post);
        }
    }

    @Transactional
    public void hardDeletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException("게시물이 없습니다.", HttpStatus.NOT_FOUND));
        hardDelete(post);
    }

    @Transactional
    public List<PostRespDto> findByCategoryId(Long categoryId, Long userId, Long notificationId, int page) {

        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException("카테고리를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (notificationId != null) {
            // 알림 읽음처리하기
            notificationService.setIsRead(notificationId);
        }


        // 카테고리에 있는 게시물 가져오기
        Pageable pageable = PageRequest.of(page,6);

        Page<Object[]> rows = postRepository.findCategoryPosts(
                categoryId,
                userId,
                pageable
        );

        categorySetService.setLastViewed(categoryId, userId);

        // 댓글수 조회
        List<Long> postIds = rows.stream()
                .map(row -> ((Post) row[0]).getId())
                .toList();

        Map<Long, Integer> commentCounts = commentService.getCommentCountsForPostIds(postIds);


        return rows.stream()
                .map(row -> {
                    Post post = (Post) row[0];
                    User user = (User) row[1];
                    int count = commentCounts.getOrDefault(post.getId(), 0);
                    return toDto(post, user, count, true);
                })
                .toList();
    }

    // 게시물 출력하기
    public List<PostRespDto> findPostToShowMainPage(Long userId, PostStatus postStatus, int page) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        List<Long> categoryIds = categoryUserRepository.findCategoriesByUserId(userId);

        // 10개씩 페이징하기
        Pageable pageable = PageRequest.of(page,3);

        Page<Object[]> rows = postRepository.findFeedPosts(
                categoryIds,
                postStatus,
                postStatus == PostStatus.ACTIVE,
                user.getId(),
                pageable
        );

        // 댓글 갯수 카운트
        Map<Long, Integer> commentCounts = commentService.getCommentCountsForPostIds(categoryIds);

        return rows.stream()
                .map(row -> {
                    Post post = (Post) row[0];
                    User postUser = (User) row[1];
                    int count = commentCounts.getOrDefault(post.getId(), 0);
                    return toDto(post, postUser, count, false);
                })
                .toList();
    }

    // 단일 게시물 상세페이지 정보
    public PostRespDto showPostDetail(Long postId) {
//        Post post = postRepository.findById(postId)
//                .orElseThrow(() -> new CustomException("게시물을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        Object[] outer = postRepository.findPostWithUser(postId)
                .orElseThrow(() -> new CustomException("게시물을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));


        if (outer.length == 0) {
            throw new CustomException("게시물을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Object[] row;
        if (outer[0] instanceof Object[]) {
            row = (Object[]) outer[0];
        } else {
            row = outer;
        }

        if (row.length < 2) {
            throw new CustomException("게시물 사용자 정보를 찾을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Post post = (Post) row[0];
        User user = (User) row[1];
        Map<Long, Integer> commentCount = commentService.getCommentCountsForPostIds(List.of(postId));

        return toDto(post, user, commentCount.getOrDefault(postId, 0), false);
    }

    // 유저 id, 타입에 따라서 게시물 조회
    public Slice<PostRespDto> findByUserId(Long userId, PostType postType, int page) {
        Pageable pageable = PageRequest.of(page,6);

        Slice<Object[]> rows = postRepository.findUserPostsWithUser(userId, postType, pageable);

        List<Long> postIds = rows.getContent().stream()
                .map(row -> ((Post) row[0]).getId())
                .toList();

        Map<Long, Integer> commentCounts = commentService.getCommentCountsForPostIds(postIds);

        return rows.map(row -> {
            Post post = (Post) row[0];
            User user = (User) row[1];
            int count = commentCounts.getOrDefault(post.getId(), 0);
            return toDto(post, user, count, false);
        });
    }

    private PostRespDto toDto(Post post, User user, int commentCount, boolean isCategorySearch) {
//        User user = userRepository.findById(post.getUserId())
//                .orElseThrow(() -> new CustomException("유저를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        String profileKey = user.getProfileImageKey();
        String fileKey;

        if (isCategorySearch) {
            fileKey = post.getFileKey();
        } else {
            fileKey = post.getThumbnailKey();
        }

        return new PostRespDto(
                post.getId(),
                user.getNickname(),
                post.getContent(),
                profileKey,
                user.getProfileImageKey().isBlank() ? null : mediaService.getPresignedUrlByKey(user.getProfileImageKey()),
                fileKey,
                post.getFileKey().isBlank() ? null : mediaService.getPresignedUrlByKey(post.getFileKey()),
                post.getAudioKey(),
                post.getWaveformData(),
                commentCount,
                post.getDuration(),
                post.getIsActive(),
                post.getCreatedAt(),
                post.getSavedAspectRatio(),
                post.getIsFromGallery(),
                post.getPostType()
        );
    }

    private List<Post> filterBlockedPosts(List<Post> posts, Long userId) {
        Set<Long> blockedUserIds = getBlockedUserIds(userId);

        return posts.stream()
                .filter(post -> !blockedUserIds.contains(post.getUserId()))
                .collect(Collectors.toList());
    }

    private Set<Long> getBlockedUserIds(Long userId) {
        List<Friend> BlockedUsers = friendRepository.findAllFriendsByUserIdAndOnlyStatus(userId, FriendStatus.BLOCKED);
        return BlockedUsers.stream()
                .map(friend -> {
                    if (friend.getReceiverId().equals(userId)) {
                        return friend.getRequesterId();
                    } else  {
                        return friend.getReceiverId();
                    }
                }).collect(Collectors.toSet());
    }
}
