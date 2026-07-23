package com.bujirun.bujirun.domain.spot.service;

import com.bujirun.bujirun.domain.spot.client.BusanAttractionApiClient;
import com.bujirun.bujirun.domain.spot.client.TourApiClient;
import com.bujirun.bujirun.domain.spot.dto.response.BusanAttractionApiResponse;
import com.bujirun.bujirun.domain.spot.dto.response.TourApiResponse.*;
import com.bujirun.bujirun.domain.spot.entity.Sigungu;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.entity.TourSpotTag;
import com.bujirun.bujirun.domain.spot.repository.SigunguRepository;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.spot.repository.TourSpotTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private final TourApiClient             tourApiClient;
    private final BusanAttractionApiClient  busanAttractionApiClient;
    private final TourSpotRepository        tourSpotRepository;
    private final TourSpotTagRepository     tourSpotTagRepository;
    private final SigunguRepository         sigunguRepository;
    private static final List<Integer> TARGET_CONTENT_TYPES = List.of(12, 14, 28,38); // 관광지, 문화시설, 레포츠, 시장
    private static final double BUSAN_ATTRACTION_MATCH_RADIUS_KM = 0.1; // 100m 이내면 같은 관광지로 판단

    private static final Map<Integer, String> CATEGORY_MAP = Map.of(
            12, "관광지",
            14, "문화시설",
            15, "행사",
            25, "여행코스",
            28, "레포츠",
            32, "숙박",
            38, "쇼핑",
            39, "음식점"
    );

    private static final Map<String, String> SIGUNGU_MAP = Map.ofEntries(
            Map.entry("1",  "중구"),
            Map.entry("2",  "서구"),
            Map.entry("3",  "동구"),
            Map.entry("4",  "영도구"),
            Map.entry("5",  "부산진구"),
            Map.entry("6",  "동래구"),
            Map.entry("7",  "남구"),
            Map.entry("8",  "북구"),
            Map.entry("9",  "해운대구"),
            Map.entry("10", "사하구"),
            Map.entry("11", "금정구"),
            Map.entry("12", "강서구"),
            Map.entry("13", "연제구"),
            Map.entry("14", "수영구"),
            Map.entry("15", "사상구"),
            Map.entry("16", "기장군")
    );

    @Transactional
    public MigrationResult runFullMigration() {
        log.info("========== 마이그레이션 시작 ==========");

        initSigungu();

        List<AreaListResponse.AreaItem> allItems = fetchAllPages();
        log.info("총 수집: {}건", allItems.size());

        int saved = 0, updated = 0, failed = 0;

        for (AreaListResponse.AreaItem item : allItems) {
            try {
                boolean isNew = upsertSpot(item);
                if (isNew) saved++; else updated++;
                Thread.sleep(200);
            } catch (Exception e) {
                log.error("[Migration] 실패 - contentId={}, {}", item.getContentid(), e.getMessage());
                failed++;
            }
        }

        MigrationResult result = new MigrationResult(allItems.size(), saved, updated, failed);
        log.info("========== 마이그레이션 완료: {} ==========", result);
        return result;
    }

    // 부산광역시_부산명소정보 API(data.go.kr 15063481)로 소개정보(부제목·상세내용·교통정보·휴무일·이용요금) 보완.
    // TourAPI는 contentId가 있어 정확히 매칭되지만 이 API는 contentId 체계가 달라서, 좌표 100m 반경 후보 중
    // 관광지명까지 일치하는 것만 최종 매칭함 (좌표만으로는 반경 내 다른 관광지와 오매칭될 수 있어서 정확도를 위해 이중 체크).
    @Transactional
    public BusanEnrichResult enrichWithBusanAttractionApi() {
        log.info("========== 부산명소정보 API 연동 시작 ==========");

        List<BusanAttractionApiResponse> items = busanAttractionApiClient.fetchAll();
        log.info("총 수집: {}건", items.size());

        int matched = 0, unmatched = 0, failed = 0;

        for (BusanAttractionApiResponse item : items) {
            try {
                Double lat = parseDouble(item.getLat());
                Double lng = parseDouble(item.getLng());
                if (lat == null || lng == null) {
                    unmatched++;
                    continue;
                }

                List<TourSpot> nearby = tourSpotRepository.findNearby(lat, lng, BUSAN_ATTRACTION_MATCH_RADIUS_KM);
                TourSpot spot = nearby.stream()
                        .filter(candidate -> namesMatch(candidate.getName(),
                                item.getMainTitle(), item.getTitle(), item.getPlace()))
                        .findFirst()
                        .orElse(null);

                if (spot == null) {
                    unmatched++;
                    continue;
                }

                spot.enrichFromBusanAttraction(
                        item.getUcSeq(),
                        item.getSubtitle(),
                        item.getItemCntnts(),
                        item.getCntctTel(),
                        item.getHomepageUrl(),
                        item.getTrfcInfo(),
                        buildBusanOperatingHours(item),
                        item.getHldyInfo(),
                        item.getUsageAmount()
                );
                tourSpotRepository.save(spot);
                matched++;
            } catch (Exception e) {
                log.error("[BusanEnrich] 실패 - UC_SEQ={}, {}", item.getUcSeq(), e.getMessage());
                failed++;
            }
        }

        BusanEnrichResult result = new BusanEnrichResult(items.size(), matched, unmatched, failed);
        log.info("========== 부산명소정보 API 연동 완료: {} ==========", result);
        return result;
    }

    // 공백·괄호·특수문자 표기 차이를 무시하고 한쪽이 다른 쪽 이름을 포함하면 같은 관광지로 판단
    private boolean namesMatch(String existingName, String... busanNames) {
        String normalizedExisting = normalizeSpotName(existingName);
        if (normalizedExisting.isEmpty()) return false;

        for (String candidate : busanNames) {
            String normalizedCandidate = normalizeSpotName(candidate);
            if (normalizedCandidate.isEmpty()) continue;
            if (normalizedExisting.equals(normalizedCandidate)
                    || normalizedExisting.contains(normalizedCandidate)
                    || normalizedCandidate.contains(normalizedExisting)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSpotName(String name) {
        if (name == null) return "";
        return name.replaceAll("[^0-9가-힣a-zA-Z]", "").toLowerCase();
    }

    private String buildBusanOperatingHours(BusanAttractionApiResponse item) {
        String day = item.getUsageDay();
        String time = item.getUsageDayWeekAndTime();
        if (day == null || day.isBlank())  return time;
        if (time == null || time.isBlank()) return day;
        return day + " " + time;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return null; }
    }

    public record BusanEnrichResult(int total, int matched, int unmatched, int failed) {
        @Override public String toString() {
            return String.format("전체=%d, 매칭=%d, 미매칭=%d, 실패=%d", total, matched, unmatched, failed);
        }
    }

    private void initSigungu() {
        SIGUNGU_MAP.forEach((code, name) -> {
            if (sigunguRepository.findByCode(code).isEmpty()) {
                sigunguRepository.save(Sigungu.builder().code(code).name(name).build());
            }
        });
        log.info("[Migration] sigungu 초기화 완료");
    }

    private List<AreaListResponse.AreaItem> fetchAllPages() {
        List<AreaListResponse.AreaItem> result = new ArrayList<>();

        for (int contentTypeId : TARGET_CONTENT_TYPES) {
            int pageNo = 1;
            int collectedForType = 0;

            while (true) {
                AreaListResponse response = tourApiClient.fetchAreaList(pageNo, contentTypeId);

                if (response == null
                        || response.getResponse().getBody().getItems() == null
                        || response.getResponse().getBody().getItems().getItem() == null) break;

                List<AreaListResponse.AreaItem> items =
                        response.getResponse().getBody().getItems().getItem();

                if (items.isEmpty()) break;

                result.addAll(items);
                collectedForType += items.size();
                log.info("[fetchAllPages] contentTypeId={}, page={}, 수집={}, 해당타입누적={}",
                        contentTypeId, pageNo, items.size(), collectedForType);

                if (collectedForType >= response.getResponse().getBody().getTotalCount()) break;
                pageNo++;
            }
        }

        return result;
    }

    private boolean upsertSpot(AreaListResponse.AreaItem item) {
        String contentId = item.getContentid();
        int contentTypeId = item.getContenttypeid();

        Optional<DetailIntroResponse.IntroItem> intro =
                tourApiClient.fetchDetailIntro(contentId, contentTypeId);

        Sigungu sigungu = sigunguRepository.findByCode(item.getSigungucode()).orElse(null);
//        String category = CATEGORY_MAP.getOrDefault(item.getContenttypeid(), "기타");
        String category = resolveCategory(item.getCat1());
        String operatingHours = intro.map(DetailIntroResponse.IntroItem::getUsetime).orElse(null);

        Optional<TourSpot> existing = tourSpotRepository.findByContentId(contentId);

        TourSpot spot;
        boolean isNew = existing.isEmpty();

        if (isNew) {
            spot = tourSpotRepository.save(TourSpot.builder()
                    .contentId(contentId)
                    .name(item.getTitle())
                    .category(category)
                    .sigungu(sigungu)
                    .lat(parseBigDecimal(item.getMapy()))
                    .lng(parseBigDecimal(item.getMapx()))
                    .address(item.getAddr1())
                    .thumbnailUrl(item.getFirstimage())
                    .operatingHours(operatingHours)
                    .contentTypeId(contentTypeId)
                    .build());
        } else {
            spot = existing.get();
            spot.update(item.getTitle(), category, sigungu,
                    parseBigDecimal(item.getMapy()), parseBigDecimal(item.getMapx()),
                    item.getAddr1(), item.getFirstimage(), operatingHours,
                    contentTypeId);
        }

        tourSpotTagRepository.deleteBySpotId(spot.getId());
        buildTags(item).forEach(tag ->
                tourSpotTagRepository.save(TourSpotTag.builder().spot(spot).tag(tag).build())
        );

        return isNew;
    }

    private List<String> buildTags(AreaListResponse.AreaItem item) {
        List<String> tags = new ArrayList<>();
        tags.add(CATEGORY_MAP.getOrDefault(item.getContenttypeid(), "기타"));
        tags.add(SIGUNGU_MAP.getOrDefault(item.getSigungucode(), "기타"));

        String t = item.getTitle().toLowerCase();
        if (t.contains("해수욕장") || t.contains("해변") || t.contains("바다")) tags.add("바다");
        if (t.contains("산")      || t.contains("숲")   || t.contains("공원")) tags.add("자연");
        if (t.contains("박물관")  || t.contains("역사")  || t.contains("문화")) tags.add("역사");
        if (t.contains("시장")    || t.contains("먹거리"))                       tags.add("맛집");
        if (t.contains("카페")    || t.contains("루프탑"))                       tags.add("카페");

        return tags;
    }

    private String resolveCategory(String cat1) {
        if (cat1 == null) return "관광지";
        return switch (cat1) {
            case "A01" -> "자연·공원";
            case "A02" -> "역사·문화";
            case "A03" -> "체험·놀이";
            case "A04" -> "쇼핑";
            case "A05" -> "음식";
            default    -> "관광지";
        };
    }

    // 기존 parseDouble 메서드 교체
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException e) { return null; }
    }

    public record MigrationResult(int total, int saved, int updated, int failed) {
        @Override public String toString() {
            return String.format("전체=%d, 신규=%d, 갱신=%d, 실패=%d", total, saved, updated, failed);
        }
    }
}