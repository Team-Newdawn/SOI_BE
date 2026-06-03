package com.soi.backend.domain.post.controller;

import com.soi.backend.domain.post.dto.PostCreateReqDto;
import com.soi.backend.domain.post.dto.PostRespDto;
import com.soi.backend.domain.post.dto.PostUpdateReqDto;
import com.soi.backend.domain.post.entity.PostStatus;
import com.soi.backend.domain.post.entity.PostType;
import com.soi.backend.domain.post.service.PostService;
import com.soi.backend.global.ApiResponseDto;
import com.soi.backend.global.metrics.PostRefreshMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/post")

@Tag(name = "post API", description = "게시물 관련 API")
public class PostController {

    private final PostService postService;
    private final PostRefreshMetricsService postRefreshMetricsService;

    @Operation(summary = "게시물 추가", description = "게시물을 추가합니다.")
    @PostMapping("/create")
    public ResponseEntity<ApiResponseDto<Boolean>> create(@RequestBody PostCreateReqDto postCreateReqDto) {
        Boolean categoryId = postService.addPostToCategory(postCreateReqDto);
        return ResponseEntity.ok(ApiResponseDto.success(categoryId,"게시물 추가 완료"));
    }

    @Operation(summary = "게시물 수정", description = "게시물을 수정합니다.")
    @PatchMapping("/update")
    public ResponseEntity<ApiResponseDto<Object>> update(@RequestBody PostUpdateReqDto postUpdateReqDto) {
        postService.updatePost(postUpdateReqDto);
        return ResponseEntity.ok(ApiResponseDto.success(null,"게시물 수정 완료"));
    }

    @Operation(summary = "게시물 상태변경", description = "게시물 상태를 변경합니다.\nACTIVE : 활성화\nSOFTDELETE : 삭제(휴지통)\nINACTIVE : 비활성화")
    @PatchMapping("/set-status")
    public ResponseEntity<ApiResponseDto<Object>> setPost(@RequestParam Long postId, PostStatus postStatus) {
        PostStatus status = postService.setPostStatus(postId, postStatus);
        return ResponseEntity.ok(ApiResponseDto.success(status,"게시물 상태변경 완료"));
    }

    @Operation(summary = "게시물 삭제", description = "id로 게시물을 삭제합니다.")
    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponseDto<Object>> delete(@RequestParam Long postId) {
        postService.hardDeletePost(postId);
        return ResponseEntity.ok(ApiResponseDto.success(null,"게시물 영구삭제 완료"));
    }

    @Operation(summary = "카테고리에 해당하는 게시물 조회", description = "카테고리 아이디, 유저아이디로 해당 카테고리에 속한 게시물을 조회합니다.\n page에 원하는 페이지 번호를 입력 0부터 시작")
    @GetMapping("/find-by/category")
    public ResponseEntity<ApiResponseDto<List<PostRespDto>>> findByCategoryId(@RequestParam Long categoryId,
                                                                              @AuthenticationPrincipal Long userId,
                                                                              @RequestParam(required = false) Long notificationId,
                                                                              @RequestParam(defaultValue = "0") int page) {
        List<PostRespDto> postRespDtos = postService.findByCategoryId(categoryId, userId, notificationId, page);
        postRefreshMetricsService.recordPostRefresh(userId, "category");
        return ResponseEntity.ok(ApiResponseDto.success(postRespDtos,"게시물 조회 완료"));
    }

    @Operation(summary = "전체 게시물 조회", description = "사용자가 포함된 카테고리의 모든 게시물을 상태 (활성화, 삭제됨, 비활성화)에따라 리턴해줌\n page에 원하는 페이지 번호를 입력 0부터 시작")
    @GetMapping("/find-all")
    public ResponseEntity<ApiResponseDto<List<PostRespDto>>> findAllByUserId(@AuthenticationPrincipal Long userId, @RequestParam PostStatus postStatus,
                                                                             @RequestParam(defaultValue = "0") int page) {
        List<PostRespDto> postRespDtos = postService.findPostToShowMainPage(userId, postStatus, page);
        postRefreshMetricsService.recordPostRefresh(userId, "feed");
        return ResponseEntity.ok(ApiResponseDto.success(postRespDtos,"전체 게시물 조회 완료"));
    }

    @Operation(summary = "단일 게시물 조회", description = "게시물 id로 해당 게시물의 상세정보를 조회합니다.")
    @GetMapping("/detail")
    public ResponseEntity<ApiResponseDto<PostRespDto>> showDetail(@RequestParam Long postId) {
        PostRespDto postRespDto = postService.showPostDetail(postId);
        return ResponseEntity.ok(ApiResponseDto.success(postRespDto,"게시물 조회 완료"));
    }

    @Operation(summary = "유저 id로 게시물 조회", description = "유저 id와 type으로 게시물을 조회합니다.")
    @GetMapping("/find/by-user-id")
    public ResponseEntity<ApiResponseDto<Slice<PostRespDto>>> findMediaByUserId(@AuthenticationPrincipal Long userId,
                                                                                @RequestParam PostType postType,
                                                                                @RequestParam int page) {
        Slice<PostRespDto> postRespDtos = postService.findByUserId(userId, postType, page);
        postRefreshMetricsService.recordPostRefresh(userId, "user_media");
        return ResponseEntity.ok(ApiResponseDto.success(postRespDtos,"게시물 조회 완료"));
    }
}
