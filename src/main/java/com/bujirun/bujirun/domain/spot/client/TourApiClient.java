package com.bujirun.bujirun.domain.spot.client;

import com.bujirun.bujirun.domain.spot.dto.response.TourApiResponse.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class TourApiClient {

    private static final String BASE_URL        = "https://apis.data.go.kr/B551011/KorService2";
    private static final int    AREA_CODE_BUSAN = 6;
    private static final int    NUM_OF_ROWS     = 1000;

    private final WebClient webClient;
    private final String    serviceKey;

    public TourApiClient(WebClient.Builder builder,
                         @Value("${tourapi.service-key}") String serviceKey) {
        this.webClient  = builder.baseUrl(BASE_URL).build();
        this.serviceKey = serviceKey;
    }

    public AreaListResponse fetchAreaList(int pageNo, int contentTypeId) {
        log.info("[TourAPI] areaBasedList - contentTypeId={}, pageNo={}", contentTypeId, pageNo);
        return webClient.get()
                .uri(uri -> uri.path("/areaBasedList2")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("MobileOS",   "ETC")
                        .queryParam("MobileApp",  "BujiRun")
                        .queryParam("_type",      "json")
                        .queryParam("contentTypeId", contentTypeId)
                        .queryParam("areaCode",   AREA_CODE_BUSAN)
                        .queryParam("numOfRows",  NUM_OF_ROWS)
                        .queryParam("pageNo",     pageNo)
                        .build())
                .retrieve()
                .bodyToMono(AreaListResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .block();
    }

    public Optional<DetailIntroResponse.IntroItem> fetchDetailIntro(String contentId, int contentTypeId) {
        try {
            DetailIntroResponse res = webClient.get()
                    .uri(uri -> uri.path("/detailIntro2")
                            .queryParam("serviceKey",    serviceKey)
                            .queryParam("MobileOS",      "ETC")
                            .queryParam("MobileApp",     "BujiRun")
                            .queryParam("_type",         "json")
                            .queryParam("contentId",     contentId)
                            .queryParam("contentTypeId", contentTypeId)
                            .build())
                    .retrieve()
                    .bodyToMono(DetailIntroResponse.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                    .block();

            return Optional.ofNullable(res)
                    .map(r -> r.getResponse().getBody().getItems().getItem())
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0));

        } catch (Exception e) {
            log.warn("[TourAPI] detailIntro 실패 - contentId={}, contentTypeId={}, {}", contentId, contentTypeId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<DetailCommonResponse.CommonItem> fetchDetailCommon(String contentId) {
        try {
            // detailCommon2는 현재 contentId 외의 파라미터(contentTypeId, defaultYN, firstImageYN,
            // addrinfoYN, mapinfoYN, overviewYN 등)를 하나라도 같이 보내면 그 파라미터를 걸어
            // INVALID_REQUEST_PARAMETER_ERROR를 반환한다(2026-08-06 실제 호출로 확인 — 예전엔
            // 됐을 수도 있으나 API 스펙이 바뀐 듯). 예전엔 이 실패가 catch에 조용히 먹혀서
            // TourAPI의 overview/tel/homepage가 항상 폴백 문구("등록된 정보 없음")로만 나갔음.
            // contentId만 보내도 overview/tel/homepage/firstimage 등은 기본으로 포함되어 내려온다.
            DetailCommonResponse res = webClient.get()
                    .uri(uri -> uri.path("/detailCommon2")
                            .queryParam("serviceKey",    serviceKey)
                            .queryParam("MobileOS",      "ETC")
                            .queryParam("MobileApp",     "BujiRun")
                            .queryParam("_type",         "json")
                            .queryParam("contentId",     contentId)
                            .build())
                    .retrieve()
                    .bodyToMono(DetailCommonResponse.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                    .block();

            return Optional.ofNullable(res)
                    .map(r -> r.getResponse().getBody().getItems().getItem())
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0));

        } catch (Exception e) {
            log.warn("[TourAPI] detailCommon 실패 - contentId={}, {}", contentId, e.getMessage());
            return Optional.empty();
        }
    }

}