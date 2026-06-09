# 방문 인증 API

GPS 좌표를 기반으로 관광지 방문을 인증합니다.

---

## POST /api/visits

### Request

```json
{
  "userId": "06160f6c-0707-4273-842c-c2e10bdc475c",
  "tourSpotId": "5f66f31b-1cd0-4bd5-905d-28741f9a4c5d",
  "gpsLat": 35.1587,
  "gpsLng": 129.1604
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | UUID | ✅ | 사용자 ID |
| tourSpotId | UUID | ✅ | 관광지 ID |
| gpsLat | Double | ✅ | 사용자 현재 위도 |
| gpsLng | Double | ✅ | 사용자 현재 경도 |

### Response

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "visitId": "5ce19ea1-cab3-4bfd-977d-2d39b13d0a33",
    "verified": true,
    "distanceMeters": 47.3
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| visitId | UUID | 생성된 방문 기록 ID |
| verified | Boolean | 인증 성공 여부 |
| distanceMeters | Double | 관광지까지 실제 거리 (미터) |

### HTTP Status

| 코드 | 설명 |
|------|------|
| 201 | 방문 기록 생성 성공 (인증 실패도 201 반환) |
| 400 | 요청값 누락 또는 관광지 좌표 정보 없음 |
| 404 | 존재하지 않는 tourSpotId |

---

## 인증 로직

1. 프론트엔드에서 브라우저 Geolocation API로 GPS 좌표 수집
2. 백엔드에서 Haversine 공식으로 관광지 좌표와의 거리 계산
3. 관광지 카테고리별 반경 이내이면 `verified: true`

### 카테고리별 인증 반경

| 카테고리 | 반경 | 해당 관광지 예시 |
|---------|------|----------------|
| 자연·공원 | 500m | 해수욕장, 넓은 공원 |
| 체험·놀이 | 200m | 야외 체험 시설 |
| 역사·문화, 쇼핑, 음식, 기타 | 100m | 박물관, 식당, 상점 |

### 주의사항

- 인증 실패(`verified: false`)도 방문 기록은 DB에 저장됩니다.
- HTTPS 환경에서만 브라우저 Geolocation API가 동작합니다.
- 실내에서는 GPS 정확도가 낮아질 수 있습니다.
