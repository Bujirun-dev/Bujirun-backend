package com.bujirun.bujirun.domain.bookmark.repository;

import com.bujirun.bujirun.domain.bookmark.entity.Bookmark;
import com.bujirun.bujirun.domain.bookmark.entity.BookmarkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookmarkRepository extends JpaRepository<Bookmark, BookmarkId> {

    Optional<Bookmark> findByUserIdAndSpotId(UUID userId, UUID spotId);

    boolean existsByUserIdAndSpotId(UUID userId, UUID spotId);

    @Query("""
        select b from Bookmark b
        join fetch b.spot s
        where b.user.id = :userId
        order by b.bookmarkedAt desc
        """)
    List<Bookmark> findAllWithSpotByUserId(@Param("userId") UUID userId);
}
