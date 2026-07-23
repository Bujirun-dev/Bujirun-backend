package com.bujirun.bujirun.domain.spot.client;

import com.bujirun.bujirun.domain.spot.dto.response.BusanAttractionApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BusanAttractionApiClient {

    private static final String BASE_URL   = "http://apis.data.go.kr/6260000/AttractionService";
    private static final int    NUM_OF_ROWS = 100;

    private final WebClient    webClient;
    private final ObjectMapper objectMapper;
    private final String       serviceKey;

    public BusanAttractionApiClient(WebClient.Builder builder, ObjectMapper objectMapper,
                                     @Value("${busan-attraction-api.service-key}") String serviceKey) {
        this.webClient    = builder.baseUrl(BASE_URL).build();
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

    private JsonNode fetchPage(int pageNo) {
        try {
            return webClient.get()
                    .uri(uri -> uri.path("/getAttractionKr")
                            .queryParam("ServiceKey", serviceKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", NUM_OF_ROWS)
                            .queryParam("resultType", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                    .block();
        } catch (Exception e) {
            log.warn("[BusanAttractionApi] page={} 조회 실패, {}", pageNo, e.getMessage());
            return null;
        }
    }

    // 최상위 응답 구조(header/body 중첩 여부, 연산명으로 감싸는지 등)가 실제 샘플로 확인되지 않아
    // "item" 필드를 가진 노드를 재귀 탐색해서 찾는다. 구조가 확인되면 이 부분만 단순화하면 됨.
    private List<BusanAttractionApiResponse> extractItems(JsonNode root) {
        JsonNode itemNode = findFieldRecursive(root, "item");
        if (itemNode == null || itemNode.isMissingNode() || itemNode.isNull()) return List.of();

        try {
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

    private Integer findIntField(JsonNode root, String fieldName) {
        JsonNode node = findFieldRecursive(root, fieldName);
        return (node != null && node.isNumber()) ? node.asInt() : null;
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
