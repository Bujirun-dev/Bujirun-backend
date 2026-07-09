package com.bujirun.bujirun.domain.bookmark.controller;

import com.bujirun.bujirun.domain.bookmark.dto.response.BookmarkListResponse;
import com.bujirun.bujirun.domain.bookmark.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "북마크", description = "관광지 북마크 목록 조회, 추가, 삭제 API")
@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "북마크 목록 조회", description = "로그인한 사용자가 북마크한 관광지 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<BookmarkListResponse>> getBookmarks(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(bookmarkService.getBookmarks(userId));
    }

    @Operation(summary = "북마크 추가", description = "관광지를 북마크에 추가합니다.")
    @PostMapping("/{spotId}")
    public ResponseEntity<Void> addBookmark(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID spotId) {
        bookmarkService.addBookmark(userId, spotId);
        return ResponseEntity.status(201).build();
    }

    @Operation(summary = "북마크 삭제", description = "관광지에 대한 북마크를 삭제합니다.")
    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> removeBookmark(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID spotId) {
        bookmarkService.removeBookmark(userId, spotId);
        return ResponseEntity.noContent().build();
    }
}
