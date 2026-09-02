package com.bujirun.bujirun.domain.itinerary.vote.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.repository.GroupRepository;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.*;
import com.bujirun.bujirun.domain.itinerary.generate.service.SubwayScheduleMappingService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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
    private final SubwayScheduleMappingService subwayScheduleMappingService;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_VISIT_DURATION_MINUTES = 60;

    // 프론트 axios 타임아웃(60초)보다 여유를 두고 대기를 포기한다.
    private static final Duration GENERATION_WAIT_TIMEOUT = Duration.ofSeconds(55);
    private static final Duration GENERATION_POLL_INTERVAL = Duration.ofMillis(1000);

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

    // 그룹당 "생성 중(generating)" + "투표 중(voting)" 자리는 합쳐서 하나만 허용됨
    // (DB 유니크 인덱스, V26 → V36에서 generating까지 확장). AI 호출(최대 60초) 전에
    // 이 자리를 먼저 선점해서, 한 그룹에서 OpenAI 생성이 동시에 여러 번 실행되어
    // 멤버마다 관광지 조합이 달라지는 문제(2026-09-02 발견)를 막는다.
    // 선점에 실패하면(다른 멤버가 이미 선점/완료함) empty를 반환하고,
    // 호출부(컨트롤러)는 waitForActiveSession()으로 그 결과를 기다려 합류해야 한다.
    public Optional<UUID> tryReserveGeneration(UUID groupId) {
        try {
            // ID가 DB 시퀀스가 아니라 Hibernate에서 UUID로 미리 채번되므로, save()만 호출하면
            // 실제 INSERT(및 유니크 제약 검사)가 트랜잭션 커밋 시점(이 메서드가 리턴한 뒤)까지
            // 미뤄질 수 있다. 그러면 여기 catch가 위반을 못 잡으므로 saveAndFlush로 즉시 반영한다.
            ItineraryVoteSession session = sessionRepository.saveAndFlush(ItineraryVoteSession.builder()
                    .groupId(groupId)
                    .status("generating")
                    .build());
            return Optional.of(session.getId());
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    // 선점한 요청이 AI 생성을 마쳤을 때 결과를 채워 "voting"으로 전이한다.
    public void completeGeneration(UUID sessionId, ItineraryGenerateResponse generated) {
        String plansJson;
        try {
            plansJson = objectMapper.writeValueAsString(generated);
        } catch (Exception e) {
            throw new RuntimeException("투표 세션 생성 실패", e);
        }
        findSession(sessionId).completeGeneration(plansJson);
    }

    // 선점한 요청이 생성 중 실패했을 때 자리를 비워서, 다음 요청이 처음부터 다시 생성할 수 있게 한다.
    public void abandonGeneration(UUID sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    // 선점에 실패한 요청이 선점한 쪽의 생성 완료를 기다린다. 프론트 타임아웃(60초)보다
    // 여유를 두고 폴링하며, 그 사이 선점한 쪽이 실패해서 자리를 비워버리면(abandonGeneration)
    // 더 기다릴 필요가 없으므로 즉시 empty를 반환해 컨트롤러가 재시도하게 한다.
    // 폴링 도중 커넥션을 계속 붙잡지 않도록 트랜잭션 밖에서 동작한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<GroupItineraryGenerateResponse> waitForActiveSession(UUID groupId) {
        Instant deadline = Instant.now().plus(GENERATION_WAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<GroupItineraryGenerateResponse> active = findActiveSession(groupId);
            if (active.isPresent()) {
                return active;
            }
            boolean stillGenerating = sessionRepository
                    .findFirstByGroupIdAndStatusOrderByCreatedAtDesc(groupId, "generating")
                    .isPresent();
            if (!stillGenerating) {
                return Optional.empty();
            }
            sleep(GENERATION_POLL_INTERVAL);
        }
        throw new IllegalStateException("그룹 일정 생성 대기 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.");
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("그룹 일정 생성 대기 중 인터럽트가 발생했습니다.", e);
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
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .accommodationName(request.getAccommodationName())
                .accommodationAddress(request.getAccommodationAddress())
                .accommodationLat(request.getAccommodationLat())
                .accommodationLng(request.getAccommodationLng())
                .build();

        for (FinalizeItineraryRequest.DayInput dayInput : days) {
            // startDate는 @NotNull이라 항상 있음 — dayNumber 기준으로 날짜 계산해서 채워야 함.
            // 이걸 빠뜨려서 date가 계속 null로 저장되던 버그(영수증 등에서 day별 날짜 미노출, 2026-08-27 발견)
            ItineraryDay day = ItineraryDay.builder()
                    .itinerary(itinerary)
                    .dayNumber(dayInput.getDay())
                    .date(itinerary.getStartAt().plusDays(dayInput.getDay() - 1))
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

                TransitDetail transitDetail = leg != null
                        ? TransitDetail.from(leg, subwayScheduleMappingService.mapSubwaySegments(leg))
                        : TransitDetail.EMPTY;

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
                        .transitDetail(transitDetail)
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