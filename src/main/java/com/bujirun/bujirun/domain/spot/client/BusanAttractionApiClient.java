package com.bujirun.bujirun.domain.spot.client;

import com.bujirun.bujirun.domain.spot.dto.response.BusanAttractionApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BusanAttractionApiClient {

    private static final String BASE_URL   = "https://apis.data.go.kr/6260000/AttractionService";
    private static final int    NUM_OF_ROWS = 100;

    private final WebClient    webClient;
    private final ObjectMapper objectMapper;
    private final String       serviceKey;

    public BusanAttractionApiClient(WebClient.Builder builder, ObjectMapper objectMapper,
                                     @Value("${busan-attraction-api.service-key}") String serviceKey) {
        // 상세내용(ITEMCNTNTS)이 항목당 수 KB라 100건씩 받으면 WebClient 기본 버퍼(256KB)를 넘어서 늘림
        this.webClient    = builder.baseUrl(BASE_URL)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;
        this.serviceKey   = serviceKey;
    }

    public List<BusanAttractionApiResponse> fetchAll() {
        List<BusanAttractionApiResponse> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            JsonNode root = fetchPage(pageNo);
            if (root == null) break;

            List<BusanAttractionApiResponse> items = extractItems(root);
            if (items.isEmpty()) break;

            result.addAll(items);
            log.info("[BusanAttractionApi] page={}, 수집={}, 누적={}", pageNo, items.size(), result.size());

            Integer totalCount = findIntField(root, "totalCount");
            if (totalCount != null && result.size() >= totalCount) break;
            pageNo++;
        }

        return result;
    }

    // 이 API 서버(VISITBUSAN)에서 확인된 특이사항 세 가지 때문에 표준 WebClient 빌더 대신 수동으로 처리한다.
    // 1) 기본 User-Agent(curl 기본값, Reactor Netty 기본값)는 403/401로 차단됨 - 브라우저 User-Agent/Referer 필요
    // 2) 서비스키에 '+' 문자가 있는데, UriComponentsBuilder.queryParam()이 이걸 그대로 통과시켜서 서버가
    //    폼 인코딩 규칙으로 '+' → 공백으로 잘못 디코딩해 401이 남. URLEncoder로 직접 인코딩해서 완성된
    //    URI 문자열을 그대로 넘겨야 함.
    // 3) 실제 JSON을 내려주면서도 Content-Type을 text/plain으로 잘못 표기해서, WebClient의 Jackson 디코더가
    //    미디어타입 불일치로 거부함 - String으로 받아서 직접 objectMapper.readTree()로 파싱해야 함.
    private JsonNode fetchPage(int pageNo) {
        try {
            String encodedKey = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
            String url = BASE_URL + "/getAttractionKr?ServiceKey=" + encodedKey
                    + "&pageNo=" + pageNo
                    + "&numOfRows=" + NUM_OF_ROWS
                    + "&resultType=json";

            String rawBody = webClient.get()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.data.go.kr/")
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                    .block();

            return rawBody == null ? null : objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("[BusanAttractionApi] page={} 조회 실패, {}", pageNo, e.getMessage());
            return null;
        }
    }

    // 포털 명세 예시가 resultCode/totalCount 같은 메타필드와 UC_SEQ 등 항목필드를 한 객체에 같이 나열해서
    // 보여줘서, 실제 응답이 (a) 그런 평평한 객체의 배열인지 (b) 단건 객체 하나인지 (c) header/body처럼
    // "item" 필드 아래 감싸져 오는지 확정할 수 없었음. 세 가지 모두 처리하도록 방어적으로 짬 - 실제 첫 응답을
    // 로그로 보고 필요하면 이 메서드만 단순화하면 됨.
    private List<BusanAttractionApiResponse> extractItems(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) return List.of();

        try {
            if (root.isArray()) {
                return objectMapper.convertValue(root,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, BusanAttractionApiResponse.class));
            }

            if (root.isObject() && root.has("UC_SEQ")) {
                return List.of(objectMapper.convertValue(root, BusanAttractionApiResponse.class));
            }

            JsonNode itemNode = findFieldRecursive(root, "item");
            if (itemNode == null || itemNode.isMissingNode() || itemNode.isNull()) return List.of();

            if (itemNode.isArray()) {
                return objectMapper.convertValue(itemNode,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, BusanAttractionApiResponse.class));
            }
            return List.of(objectMapper.convertValue(itemNode, BusanAttractionApiResponse.class));
        } catch (Exception e) {
            log.warn("[BusanAttractionApi] item 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    // totalCount/pageNo 등이 숫자가 아니라 문자열("107")로 내려올 수 있어 asInt()로 통일해서 파싱
    private Integer findIntField(JsonNode root, String fieldName) {
        if (root == null) return null;

        if (root.isArray()) {
            for (JsonNode el : root) {
                if (el.isObject() && el.has(fieldName)) return el.get(fieldName).asInt();
            }
            return null;
        }

        JsonNode node = findFieldRecursive(root, fieldName);
        return node != null ? node.asInt() : null;
    }

    private JsonNode findFieldRecursive(JsonNode node, String fieldName) {
        if (node == null || !node.isObject()) return null;
        if (node.has(fieldName)) return node.get(fieldName);

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isObject()) {
                JsonNode found = findFieldRecursive(entry.getValue(), fieldName);
                if (found != null) return found;
            }
        }
        return null;
    }
}
