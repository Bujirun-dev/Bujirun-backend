package com.bujirun.bujirun.domain.itinerary.vote.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.repository.GroupRepository;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.*;
import com.bujirun.bujirun.domain.itinerary.generate.service.TransitRouteService;
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
import com.bujirun.bujirun.global.util.TransitRouteUtils;
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
    private final TransitRouteService transitRouteService;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_VISIT_DURATION_MINUTES = 60;

    public Optional<GroupItineraryGenerateResponse> findActiveSession(UUID groupId) {
        return sessionRepository.findFirstByGroupIdAndStatusOrderByCreatedAtDesc(groupId, "voting")
                .map(session -> {
                    try {
                        ItineraryGenerateResponse plans =
                                objectMapper.readValue(session.getPlansJson(), ItineraryGenerateResponse.class);
                        return GroupItineraryGenerateResponse.builder()
                                .voteSessionId(session.getId())
                                .plans(plans)
                                .build();
                    } catch (Exception e) {
                        throw new RuntimeException("일정 데이터 파싱 실패", e);
                    }
                });
    }

    // 그룹당 진행 중(voting) 세션은 DB 유니크 인덱스(V26)로 하나만 허용됨.
    // 동시에 여러 멤버가 투표를 시작하면 먼저 커밋된 쪽만 성공하고 나머지는
    // DataIntegrityViolationException이 그대로 던져지니, 호출부(컨트롤러)에서
    // 이를 잡아 새로 만들지 않고 기존 세션을 재조회해서 합류시켜야 한다.
    public UUID startVoteSession(UUID groupId, ItineraryGenerateResponse generated) {
        String plansJson;
        try {
            plansJson = objectMapper.writeValueAsString(generated);
        } catch (Exception e) {
            throw new RuntimeException("투표 세션 생성 실패", e);
        }
        ItineraryVoteSession session = sessionRepository.save(ItineraryVoteSession.builder()
                .groupId(groupId)
                .plansJson(plansJson)
                .status("voting")
                .build());
        return session.getId();
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
        // 확정된 세션도 조회는 가능해야 한다 — 프론트가 finalize 직후에도 이 API를
        // 폴링해서 status가 "confirmed"로 바뀐 걸 보고 화면을 전환하기 때문에,
        // getVotingSession()의 "확정된 세션 거부" 체크를 여기선 쓰면 안 된다.
        ItineraryVoteSession session = findSession(sessionId);
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
        // 여행 하나당 그룹 하나 정책 — 그 사이 다른 세션이 먼저 확정했을 수 있으므로 저장 직전에도 재확인
        if (itineraryRepository.existsByGroupId(groupId)) {
            throw new IllegalStateException("이미 이 그룹의 일정이 확정되어 있습니다.");
        }

        List<FinalizeItineraryRequest.DayInput> days = request.getDays() != null
                ? request.getDays()
                : extractDaysFromPlan(session.getPlansJson(), finalPlan);

        validateDays(finalPlan, days);

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

            List<TourSpot> spots = dayInput.getSpotContentIds().stream()
                    .map(contentId -> tourSpotRepository.findByContentId(contentId)
                            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관광지: " + contentId)))
                    .toList();

            List<TransitRouteResponse> routes = transitRouteService.getRoutesForDay(
                    spots.stream().map(this::toSpotInfo).toList(), null);

            int order = 1;
            for (int i = 0; i < spots.size(); i++) {
                TransitOption leg = (i == 0 || routes.get(i - 1).options().isEmpty())
                        ? null
                        : routes.get(i - 1).options().get(0);

                SubPath firstTransitSubPath = leg != null
                        ? TransitRouteUtils.findFirstTransitSubPath(leg.subPaths())
                        : null;

                ItineraryItem item = ItineraryItem.builder()
                        .day(day)
                        .spot(spots.get(i))
                        .orderIndex(order++)
                        .durationMin(DEFAULT_VISIT_DURATION_MINUTES)
                        .travelMode(leg != null ? toTravelMode(leg.type()) : null)
                        .travelTimeMin(leg != null ? leg.totalTime() : null)
                        .routeType(firstTransitSubPath != null ? firstTransitSubPath.type() : (leg != null ? leg.type() : null))
                        .routeNo(firstTransitSubPath != null ? firstTransitSubPath.routeNo() : null)
                        .startStationName(firstTransitSubPath != null ? firstTransitSubPath.startName() : null)
                        .endStationName(firstTransitSubPath != null ? firstTransitSubPath.endName() : null)
                        .startArsId(firstTransitSubPath != null ? firstTransitSubPath.startArsId() : null)
                        .build();
                
                day.getItems().add(item);
            }
            itinerary.getDays().add(day);
        }

        return itineraryRepository.save(itinerary).getId();
    }

    // ODsay/자체계산 TransitOption.type()의 한글 값을 DB travel_mode 허용값(walk/transit/taxi)으로 변환
    private String toTravelMode(String type) {
        return switch (type) {
            case "도보" -> "walk";
            case "택시" -> "taxi";
            default -> "transit"; // "대중교통" 등
        };
    }

    private SpotInfo toSpotInfo(TourSpot spot) {
        return SpotInfo.builder()
                .contentId(spot.getContentId())
                .name(spot.getName())
                .category(spot.getCategory())
                .lat(spot.getLat() != null ? spot.getLat().doubleValue() : 0)
                .lng(spot.getLng() != null ? spot.getLng().doubleValue() : 0)
                .address(spot.getAddress())
                .thumbnailUrl(spot.getThumbnailUrl())
                .operatingHours(spot.getOperatingHours())
                .build();
    }

    private void validateDays(String finalPlan, List<FinalizeItineraryRequest.DayInput> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException(
                    "C".equals(finalPlan)
                            ? "C안(자유 편집형)은 최소 1개 이상의 day 정보가 필요합니다."
                            : "일정에 최소 1개 이상의 day가 필요합니다.");
        }

        // C안(자유 편집형)은 프론트에서 빈 day 배열을 만들어 보내는 게 정상 플로우이므로
        // day당 관광지 개수 검증은 건너뜀
        if ("C".equals(finalPlan)) {
            return;
        }
        
        for (FinalizeItineraryRequest.DayInput day : days) {
            if (day.getSpotContentIds() == null || day.getSpotContentIds().isEmpty()) {
                throw new IllegalArgumentException(
                        "day " + day.getDay() + "에 최소 1개 이상의 관광지가 필요합니다.");
            }
            if (new HashSet<>(day.getSpotContentIds()).size() != day.getSpotContentIds().size()) {
                throw new IllegalArgumentException(
                        "day " + day.getDay() + "에 같은 관광지가 중복으로 포함되어 있습니다.");
            }
        }
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
                .confirmedPlan(session.getConfirmedPlan())
                .itineraryId(session.getItineraryId())
                .build();
    }

    private ItineraryVoteSession getVotingSession(UUID sessionId) {
        ItineraryVoteSession session = findSession(sessionId);
        if ("confirmed".equals(session.getStatus())) {
            throw new IllegalStateException("이미 확정된 일정입니다.");
        }
        return session;
    }

    private ItineraryVoteSession findSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("투표 세션을 찾을 수 없습니다."));
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