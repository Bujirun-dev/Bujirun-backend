package com.bujirun.bujirun.domain.spot.service;

import com.bujirun.bujirun.domain.spot.client.TourApiClient;
import com.bujirun.bujirun.domain.spot.dto.TourApiResponse.*;
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

    private final TourApiClient         tourApiClient;
    private final TourSpotRepository    tourSpotRepository;
    private final TourSpotTagRepository tourSpotTagRepository;
    private final SigunguRepository     sigunguRepository;
    private static final List<Integer> TARGET_CONTENT_TYPES = List.of(12, 14, 28); // 관광지, 문화시설, 레포츠

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
                    .build());
        } else {
            spot = existing.get();
            spot.update(item.getTitle(), category, sigungu,
                    parseBigDecimal(item.getMapy()), parseBigDecimal(item.getMapx()),
                    item.getAddr1(), item.getFirstimage(), operatingHours);
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