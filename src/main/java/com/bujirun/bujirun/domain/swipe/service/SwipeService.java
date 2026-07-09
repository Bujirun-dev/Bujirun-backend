package com.bujirun.bujirun.domain.swipe.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.swipe.dto.request.SwipeRequest;
import com.bujirun.bujirun.domain.swipe.dto.request.SwipeSubmitRequest;
import com.bujirun.bujirun.domain.swipe.dto.response.SwipeSessionResponse;
import com.bujirun.bujirun.domain.swipe.entity.SwipeResult;
import com.bujirun.bujirun.domain.swipe.entity.SwipeSession;
import com.bujirun.bujirun.domain.swipe.repository.SwipeResultRepository;
import com.bujirun.bujirun.domain.swipe.repository.SwipeSessionRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SwipeService {

    private final SwipeSessionRepository swipeSessionRepository;
    private final SwipeResultRepository swipeResultRepository;
    private final TourSpotRepository tourSpotRepository;
    private final GroupMemberRepository groupMemberRepository;

    public SwipeSessionResponse submitSwipeSession(SwipeSubmitRequest request, UUID userId) {

        if (request.getGroupId() != null
                && !groupMemberRepository.existsById_GroupIdAndId_UserId(request.getGroupId(), userId)) {
            throw new IllegalArgumentException("그룹 멤버만 그룹 스와이프 결과를 제출할 수 있습니다.");
        }

        List<String> contentIds = request.getSwipes().stream()
                .map(SwipeRequest.SwipeItem::getContentId)
                .distinct()
                .toList();

        Map<String, TourSpot> spotsByContentId = tourSpotRepository.findByContentIdIn(contentIds).stream()
                .collect(Collectors.toMap(TourSpot::getContentId, spot -> spot));

        List<String> missingIds = contentIds.stream()
                .filter(id -> !spotsByContentId.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 관광지 contentId: " + missingIds);
        }

        SwipeSession session = swipeSessionRepository.save(SwipeSession.builder()
                .userId(userId)
                .groupId(request.getGroupId())
                .status("completed")
                .build());

        List<SwipeResult> results = request.getSwipes().stream()
                .map(item -> SwipeResult.builder()
                        .session(session)
                        .spot(spotsByContentId.get(item.getContentId()))
                        .liked(item.isLiked())
                        .build())
                .toList();
        swipeResultRepository.saveAll(results);

        log.info("[스와이프 결과 저장] userId={}, groupId={}, sessionId={}, count={}",
                userId, request.getGroupId(), session.getId(), results.size());

        return SwipeSessionResponse.builder()
                .sessionId(session.getId())
                .groupId(session.getGroupId())
                .status(session.getStatus())
                .resultCount(results.size())
                .createdAt(session.getCreatedAt())
                .build();
    }
}