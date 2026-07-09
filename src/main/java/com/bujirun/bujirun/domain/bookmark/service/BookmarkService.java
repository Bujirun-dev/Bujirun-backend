package com.bujirun.bujirun.domain.bookmark.service;

import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.bookmark.dto.response.BookmarkListResponse;
import com.bujirun.bujirun.domain.bookmark.entity.Bookmark;
import com.bujirun.bujirun.domain.bookmark.repository.BookmarkRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final TourSpotRepository tourSpotRepository;
    private final UserRepository userRepository;

    public List<BookmarkListResponse> getBookmarks(UUID userId) {
        return bookmarkRepository.findAllWithSpotByUserId(userId).stream()
                .map(BookmarkListResponse::from)
                .toList();
    }

    @Transactional
    public void addBookmark(UUID userId, UUID spotId) {
        if (bookmarkRepository.existsByUserIdAndSpotId(userId, spotId)) {
            return;
        }

        TourSpot spot = tourSpotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("관광지를 찾을 수 없습니다. id=" + spotId));
        User user = userRepository.getReferenceById(userId);

        bookmarkRepository.save(Bookmark.builder().user(user).spot(spot).build());
    }

    @Transactional
    public void removeBookmark(UUID userId, UUID spotId) {
        Bookmark bookmark = bookmarkRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new EntityNotFoundException("북마크를 찾을 수 없습니다. spotId=" + spotId));
        bookmarkRepository.delete(bookmark);
    }
}
