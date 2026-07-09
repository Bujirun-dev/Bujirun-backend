package com.bujirun.bujirun.domain.itinerary.vote.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.repository.GroupRepository;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryRepository;
import com.bujirun.bujirun.domain.itinerary.vote.dto.request.CastVoteRequest;
import com.bujirun.bujirun.domain.itinerary.vote.dto.request.FinalizeItineraryRequest;
import com.bujirun.bujirun.domain.itinerary.vote.dto.response.VoteStatusResponse;
import com.bujirun.bujirun.domain.itinerary.vote.entity.ItineraryVote;
import com.bujirun.bujirun.domain.itinerary.vote.entity.ItineraryVoteSession;
import com.bujirun.bujirun.domain.itinerary.vote.repository.ItineraryVoteRepository;
import com.bujirun.bujirun.domain.itinerary.vote.repository.ItineraryVoteSessionRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItineraryVoteService {

    private final ItineraryVoteSessionRepository sessionRepository;
    private final ItineraryVoteRepository voteRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final ItineraryRepository itineraryRepository;
    private final TourSpotRepository tourSpotRepository;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_VISIT_DURATION_MINUTES = 60;

    public UUID startVoteSession(UUID groupId, ItineraryGenerateResponse generated) {
        try {
            String plansJson = objectMapper.writeValueAsString(generated);
            ItineraryVoteSession session = sessionRepository.save(ItineraryVoteSession.builder()
                    .groupId(groupId)
                    .plansJson(plansJson)
                    .status("voting")
                    .build());
            return session.getId();
        } catch (Exception e) {
            throw new RuntimeException("투표 세션 생성 실패", e);
        }
    }

    public VoteStatusResponse castVote(UUID sessionId, CastVoteRequest request, UUID userId) {
        ItineraryVoteSession session = getVotingSession(sessionId);
        validateGroupMember(session.getGroupId(), userId);

        voteRepository.findBySessionIdAndUserId(sessionId, userId)
                .ifPresentOrElse(
                        v -> { throw new IllegalArgumentException("이미 투표했습니다."); },
                        () -> voteRepository.save(ItineraryVote.builder()
                                .sessionId(sessionId)
                                .userId(userId)
                                .votedPlan(request.getVotedPlan())
                                .build())
                );

        return buildVoteStatus(session);
    }

    public VoteStatusResponse getVoteStatus(UUID sessionId, UUID userId) {
        ItineraryVoteSession session = getVotingSession(sessionId);
        validateGroupMember(session.getGroupId(), userId);
        return buildVoteStatus(session);
    }

    public UUID finalizeByLeader(UUID sessionId, FinalizeItineraryRequest request, UUID userId) {
        ItineraryVoteSession session = getVotingSession(sessionId);

        if (!isGroupLeader(session.getGroupId(), userId)) {
            throw new IllegalArgumentException("그룹 리더만 일정을 확정할 수 있습니다.");
        }

        String finalPlan = Boolean.TRUE.equals(request.getFreePass())
                ? request.getSelectedPlan()
                : resolveWinningPlan(sessionId, request.getSelectedPlan());

        UUID itineraryId = saveConfirmedItinerary(session.getGroupId(), finalPlan, session, request);
        session.confirm(finalPlan, itineraryId);

        log.info("[그룹 일정 확정] groupId={}, sessionId={}, plan={}, freePass={}",
                session.getGroupId(), sessionId, finalPlan, request.getFreePass());

        return itineraryId;
    }

    private String resolveWinningPlan(UUID sessionId, String tieBreakPlan) {
        List<ItineraryVote> votes = voteRepository.findBySessionId(sessionId);
        if (votes.isEmpty()) {
            throw new IllegalStateException("투표 결과가 없습니다. 프리패스를 사용하거나 투표를 기다려주세요.");
        }

        Map<String, Long> counts = votes.stream()
                .collect(Collectors.groupingBy(ItineraryVote::getVotedPlan, Collectors.counting()));

        long maxCount = Collections.max(counts.values());
        List<String> topPlans = counts.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .toList();

        if (topPlans.size() == 1) {
            return topPlans.get(0);
        }

        if (tieBreakPlan == null || !topPlans.contains(tieBreakPlan)) {
            throw new IllegalArgumentException("동률입니다. 동률 후보 중 하나(" + topPlans + ")를 selectedPlan으로 지정해주세요.");
        }
        return tieBreakPlan;
    }

    private UUID saveConfirmedItinerary(UUID groupId, String finalPlan, ItineraryVoteSession session,
                                        FinalizeItineraryRequest request) {
        List<FinalizeItineraryRequest.DayInput> days = request.getDays() != null
                ? request.getDays()
                : extractDaysFromPlan(session.getPlansJson(), finalPlan);

        Itinerary itinerary = Itinerary.builder()
                .userId(request.getRequesterId())
                .groupId(groupId)
                .planType(finalPlan)
                .status("confirmed")
                .title(request.getTitle())
                .startAt(request.getStartDate())
                .endAt(request.getEndDate())
                .build();

        for (FinalizeItineraryRequest.DayInput dayInput : days) {
            ItineraryDay day = ItineraryDay.builder()
                    .itinerary(itinerary)
                    .dayNumber(dayInput.getDay())
                    .build();

            int order = 1;
            for (String contentId : dayInput.getSpotContentIds()) {
                TourSpot spot = tourSpotRepository.findByContentId(contentId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관광지: " + contentId));
                ItineraryItem item = ItineraryItem.builder()
                        .day(day)
                        .spot(spot)
                        .orderIndex(order++)
                        .durationMin(DEFAULT_VISIT_DURATION_MINUTES)
                        .build();
                day.getItems().add(item);
            }
            itinerary.getDays().add(day);
        }

        return itineraryRepository.save(itinerary).getId();
    }

    private List<FinalizeItineraryRequest.DayInput> extractDaysFromPlan(String plansJson, String plan) {
        try {
            ItineraryGenerateResponse generated = objectMapper.readValue(plansJson, ItineraryGenerateResponse.class);
            ItineraryGenerateResponse.PlanOption selected = switch (plan) {
                case "A" -> generated.getPlanA();
                case "B" -> generated.getPlanB();
                default -> throw new IllegalArgumentException("C안은 프론트 편집 결과(days)를 함께 보내야 합니다.");
            };
            return selected.getDays().stream()
                    .map(d -> new FinalizeItineraryRequest.DayInput(
                            d.getDay(),
                            d.getSpots().stream().map(SpotInfo::getContentId).toList()))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("일정 데이터 파싱 실패", e);
        }
    }

    private VoteStatusResponse buildVoteStatus(ItineraryVoteSession session) {
        List<ItineraryVote> votes = voteRepository.findBySessionId(session.getId());
        Map<String, Long> counts = votes.stream()
                .collect(Collectors.groupingBy(ItineraryVote::getVotedPlan, Collectors.counting()));
        return VoteStatusResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .voteCounts(counts)
                .totalVotes(votes.size())
                .build();
    }

    private ItineraryVoteSession getVotingSession(UUID sessionId) {
        ItineraryVoteSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("투표 세션을 찾을 수 없습니다."));
        if ("confirmed".equals(session.getStatus())) {
            throw new IllegalStateException("이미 확정된 일정입니다.");
        }
        return session;
    }

    private void validateGroupMember(UUID groupId, UUID userId) {
        if (!groupMemberRepository.existsById_GroupIdAndId_UserId(groupId, userId)) {
            throw new IllegalArgumentException("그룹 멤버만 투표할 수 있습니다.");
        }
    }

    private boolean isGroupLeader(UUID groupId, UUID userId) {
        return groupRepository.findById(groupId)
                .map(g -> g.getCreatedBy().equals(userId))
                .orElse(false);
    }
}