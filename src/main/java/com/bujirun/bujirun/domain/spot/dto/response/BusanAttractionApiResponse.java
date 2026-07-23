package com.bujirun.bujirun.domain.spot.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 부산광역시_부산명소정보 API(data.go.kr 15063481, getAttractionKr) 응답 항목.
// 포털 Swagger 예시가 resultCode/totalCount 같은 페이징 메타필드와 항목필드를 한 객체에 같이 나열해서
// 보여주는 형태라 실제 응답이 이 객체들의 배열인지, 단건 객체인지, header/body로 감싸져 오는지는 실제
// 호출 전까지 확정하지 못함 - BusanAttractionApiClient가 세 경우 모두 처리하도록 되어있음.
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

    @JsonProperty("PLACE")
    private String place;              // 여행지

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
