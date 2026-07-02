package com.bujirun.bujirun.domain.spot.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class TourApiResponse {

    // ── areaBasedList2 ──────────────────────────────────────
    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AreaListResponse {
        private Response response;

        @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Response { private Body body; }

        @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Body {
            private Items items;
            private int totalCount;
            private int numOfRows;
            private int pageNo;
        }

        @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Items { private List<AreaItem> item; }

        @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
        public static class AreaItem {
            private String contentid;
            private String title;
            private String addr1;
            private String sigungucode;
            private Integer contenttypeid;
            private String firstimage;
            private String mapx;        // 경도 → lng
            private String mapy;        // 위도 → lat
            private String cat1;        // 카테고리
        }
    }

    // ── detailIntro2 ────────────────────────────────────────
    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetailIntroResponse {
        private Response response;

        @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Response { private Body body; }

        @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Body { private Items items; }

        @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Items { private List<IntroItem> item; }

        @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
        public static class IntroItem {
            private String usetime;     // 운영시간 → operating_hours
        }
    }
}