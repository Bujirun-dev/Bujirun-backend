package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import io.netty.channel.ChannelOption;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import reactor.netty.http.client.HttpClient;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
public class BusArrivalService implements ArrivalInfoProvider {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);
    private static final int CONNECT_TIMEOUT_MILLIS = 2000;
    private static final String BASE_URL = "http://apis.data.go.kr/6260000/BusanBIMS/bitArrByArsno";

    private final WebClient webClient;
    private final String encodedApiKey;

    // 서비스키에 '+' 등 특수문자가 있으면 UriComponentsBuilder.queryParam()이 그대로 통과시켜서
    // 서버가 폼 인코딩 규칙으로 잘못 디코딩하는 문제(TourApiClient/BusanAttractionApiClient와 동일 케이스)를
    // 피하려고 URLEncoder로 미리 인코딩해서 완성된 URI 문자열을 그대로 넘긴다.
    public BusArrivalService(@Value("${busan.api.key}") String apiKey) {
        this.encodedApiKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .resolver(DefaultAddressResolverGroup.INSTANCE)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                                .responseTimeout(RESPONSE_TIMEOUT)
                ))
                .build();
    }

    @Override
    public boolean supports(String type) {
        return "버스".equals(type);
    }

    @Override
    public Integer getNextArrival(SubPath subPath) {
        String arsId = subPath.startArsId();
        log.info("버스 도착정보 조회 시작 — arsId={}, routeNo={}", arsId, subPath.routeNo());

        if (arsId == null || arsId.isBlank()) {
            log.info("버스 ARS 번호 없음 — {} 정류장", subPath.startName());
            return null;
        }
        try {
            String xml = fetchArrivalXml(arsId);
            return parseMin1(xml, subPath.routeNo());
        } catch (Exception e) {
            log.warn("버스 도착정보 조회 실패 arsId={}: {}", arsId, e.getMessage());
            return null;
        }
    }

    public Integer getArrivalByArsId(String arsId, String routeNo) {
        if (arsId == null || arsId.isBlank()) return null;
        try {
            String xml = fetchArrivalXml(arsId);
            return parseMin1(xml, routeNo);
        } catch (Exception e) {
            log.warn("버스 도착정보 조회 실패 arsId={}: {}", arsId, e.getMessage());
            return null;
        }
    }

    private String fetchArrivalXml(String arsId) {
        String url = BASE_URL + "?serviceKey=" + encodedApiKey + "&arsno=" + arsId;
        return webClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(RESPONSE_TIMEOUT)
                .block();
    }
    
    private Integer parseMin1(String xml, String routeNo) throws Exception {
        log.info("parseMin1 호출 — routeNo={}, xml 길이={}", routeNo, xml.length());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));


        NodeList items = doc.getElementsByTagName("item");
        log.info("item 개수={}", items.getLength());
        for (int i = 0; i < items.getLength(); i++) {
            org.w3c.dom.Element item = (org.w3c.dom.Element) items.item(i);
            String lineno = item.getElementsByTagName("lineno").item(0).getTextContent().trim();
            log.info("비교: [{}] vs [{}]", routeNo, lineno);
            if (routeNo.equals(lineno)) {
                NodeList min1List = item.getElementsByTagName("min1");
                log.info("매칭됨 — routeNo={}, min1 개수={}", routeNo, min1List.getLength());
                if (min1List.getLength() > 0) {
                    return Integer.parseInt(min1List.item(0).getTextContent().trim());
                }
                log.info("버스 {}번 min1 없음 (심야 등)", routeNo);
                return null;
            }
        }
        log.info("버스 {}번 도착정보 없음", routeNo);
        return null;
    }
}