package com.bujirun.bujirun.domain.spot.client;

import com.bujirun.bujirun.domain.spot.dto.response.TourApiResponse.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class TourApiClient {

    private static final String BASE_URL        = "https://apis.data.go.kr/B551011/KorService2";
    private static final int    AREA_CODE_BUSAN = 6;
    private static final int    NUM_OF_ROWS     = 1000;

    private final WebClient webClient;
    private final String    encodedServiceKey;

    // serviceKey에 '+'가 들어있으면 UriComponentsBuilder.queryParam()이 그대로 통과시켜서
    // 서버가 폼 인코딩 규칙으로 '+' → 공백으로 잘못 디코딩해 SERVICE_KEY_IS_NOT_REGISTERED_ERROR가 남
    // (BusanAttractionApiClient에서 먼저 발견된 것과 동일한 문제). URLEncoder로 미리 인코딩해서
    // 완성된 URI 문자열을 그대로 넘긴다.
    public TourApiClient(WebClient.Builder builder,
                         @Value("${tourapi.service-key}") String serviceKey) {
        this.webClient         = builder.baseUrl(BASE_URL).build();
        this.encodedServiceKey = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
    }

    public AreaListResponse fetchAreaList(int pageNo, int contentTypeId) {
        log.info("[TourAPI] areaBasedList - contentTypeId={}, pageNo={}", contentTypeId, pageNo);
        String url = BASE_URL + "/areaBasedList2?serviceKey=" + encodedServiceKey
                + "&MobileOS=ETC&MobileApp=BujiRun&_type=json"
                + "&contentTypeId=" + contentTypeId
                + "&areaCode=" + AREA_CODE_BUSAN
                + "&numOfRows=" + NUM_OF_ROWS
                + "&pageNo=" + pageNo;
        return webClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(AreaListResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .block();
    }

    public Optional<DetailIntroResponse.IntroItem> fetchDetailIntro(String contentId, int contentTypeId) {
        try {
            String url = BASE_URL + "/detailIntro2?serviceKey=" + encodedServiceKey
                    + "&MobileOS=ETC&MobileApp=BujiRun&_type=json"
                    + "&contentId=" + contentId
                    + "&contentTypeId=" + contentTypeId;
            DetailIntroResponse res = webClient.get()
                    .uri(URI.create(url))
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
            String url = BASE_URL + "/detailCommon2?serviceKey=" + encodedServiceKey
                    + "&MobileOS=ETC&MobileApp=BujiRun&_type=json"
                    + "&contentId=" + contentId;
            DetailCommonResponse res = webClient.get()
                    .uri(URI.create(url))
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