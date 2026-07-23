package com.bujirun.bujirun.domain.spot.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 부산광역시_부산명소정보 API(data.go.kr 15063481, getAttractionKr) 응답 항목.
// 포털 페이지에 실제 요청/응답 예시(원문 JSON)가 공개돼 있지 않아 최상위 응답 구조(header/body 중첩 방식)는
// 확정하지 못했음 - 필드명 자체는 "출력결과" 명세 표에서 확인함. 그래서 이 항목만 정의해두고, 실제 파싱은
// BusanAttractionApiClient에서 중첩 구조와 무관하게 이 필드를 가진 노드를 재귀 탐색해서 매핑함.
// 서비스키 발급 후 첫 실제 호출 결과를 보고 구조가 다르면 클라이언트만 손보면 됨.
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusanAttractionApiResponse {

    @JsonProperty("UC_SEQ")
    private String ucSeq;              // 콘텐츠ID

    @JsonProperty("MAIN_TITLE")
    private String mainTitle;          // 콘텐츠명

    @JsonProperty("GUGUN_NM")
    private String gugunNm;            // 구군

    @JsonProperty("LAT")
    private String lat;

    @JsonProperty("LNG")
    private String lng;

    @JsonProperty("TITLE")
    private String title;

    @JsonProperty("SUBTITLE")
    private String subtitle;

    @JsonProperty("ADDR1")
    private String addr1;

    @JsonProperty("CNTCT_TEL")
    private String cntctTel;

    @JsonProperty("HOMEPAGE_URL")
    private String homepageUrl;

    @JsonProperty("TRFC_INFO")
    private String trfcInfo;           // 교통정보

    @JsonProperty("USAGE_DAY")
    private String usageDay;           // 운영일

    @JsonProperty("HLDY_INFO")
    private String hldyInfo;           // 휴무일

    @JsonProperty("USAGE_DAY_WEEK_AND_TIME")
    private String usageDayWeekAndTime; // 운영 및 시간

    @JsonProperty("USAGE_AMOUNT")
    private String usageAmount;        // 이용요금

    @JsonProperty("MAIN_IMG_NORMAL")
    private String mainImgNormal;

    @JsonProperty("MAIN_IMG_THUMB")
    private String mainImgThumb;

    @JsonProperty("ITEMCNTNTS")
    private String itemCntnts;         // 상세내용
}
