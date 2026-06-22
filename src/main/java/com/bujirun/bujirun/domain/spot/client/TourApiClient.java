package com.bujirun.bujirun.domain.spot.client;

import com.bujirun.bujirun.domain.spot.dto.TourApiResponse.*;
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
}