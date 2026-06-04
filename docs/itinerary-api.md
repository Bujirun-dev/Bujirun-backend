# 일정(Itinerary) API 명세

Base URL: `http://localhost:8080`

---

## 공통

### 응답 형식
```json
{
  "success": true,
  "message": "OK",
  "data": { }
}
```

### 에러 응답
```json
{
  "success": false,
  "message": "에러 메시지",
  "data": null
}
```

| HTTP 상태 | 상황 |
|-----------|------|
| 400 | 요청 값 유효성 오류 |
| 404 | 리소스를 찾을 수 없음 |
| 500 | 서버 내부 오류 |

---

## 일정 (Itinerary)

### 일정 생성
`POST /api/itineraries`

**Request Body**
```json
{
  "userId": "06160f6c-0707-4273-842c-c2e10bdc475c",
  "planType": "A",
  "title": "부산 2박 3일"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | UUID | ✅ | 사용자 ID |
| planType | String | ❌ | `A` / `B` / `C` (기본값: `A`) |
| title | String | ❌ | 일정 제목 |

**Response** `201 Created`
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": "68acbcac-afe9-496b-b20b-50cb5f3cf46a",
    "userId": "06160f6c-0707-4273-842c-c2e10bdc475c",
    "title": "부산 2박 3일",
    "planType": "A",
    "status": "draft",
    "createdAt": "2026-06-04T21:50:47.469107",
    "updatedAt": "2026-06-04T21:50:47.469107",
    "days": []
  }
}
```

---

### 일정 상세 조회
`GET /api/itineraries/{id}`

**Path Variable**

| 이름 | 타입 | 설명 |
|------|------|------|
| id | UUID | 일정 ID |

**Response** `200 OK`
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": "68acbcac-afe9-496b-b20b-50cb5f3cf46a",
    "userId": "06160f6c-0707-4273-842c-c2e10bdc475c",
    "title": "부산 2박 3일",
    "planType": "A",
    "status": "draft",
    "createdAt": "2026-06-04T21:50:47.469107",
    "updatedAt": "2026-06-04T21:50:47.469107",
    "days": [
      {
        "id": "66bec83a-b4b1-49d5-8d0d-5eb1c3447795",
        "dayNumber": 1,
        "date": "2026-07-01",
        "items": [
          {
            "id": "80ad1a41-73b4-4156-8161-4ce664f79560",
            "orderIndex": 0,
            "spot": {
              "id": "5f66f31b-1cd0-4bd5-905d-28741f9a4c5d",
              "name": "해운대 해수욕장",
              "address": "부산 해운대구",
              "lat": 35.1587000,
              "lng": 129.1604000,
              "thumbnailUrl": null
            },
            "arrivalTime": "10:00:00",
            "durationMin": 120,
            "travelMode": "transit",
            "travelTimeMin": 20,
            "memo": "점심 먹고 이동"
          }
        ]
      }
    ]
  }
}
```

---

### 내 일정 목록 조회
`GET /api/itineraries?userId={userId}`

**Query Parameter**

| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | UUID | ✅ | 사용자 ID |

**Response** `200 OK`
```json
{
  "success": true,
  "message": "OK",
  "data": [
    {
      "id": "68acbcac-afe9-496b-b20b-50cb5f3cf46a",
      "title": "부산 2박 3일",
      "planType": "A",
      "status": "draft",
      "createdAt": "2026-06-04T21:50:47.469107",
      "updatedAt": "2026-06-04T21:50:47.469107"
    }
  ]
}
```

---

### 일정 수정
`PATCH /api/itineraries/{id}`

**Request Body** (모든 필드 선택)
```json
{
  "title": "부산 여름 여행",
  "status": "confirmed"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| title | String | 일정 제목 |
| status | String | `draft` / `confirmed` |

**Response** `200 OK` — 수정된 일정 상세 반환

---

### 일정 삭제
`DELETE /api/itineraries/{id}`

하위 Day, Item 모두 cascade 삭제됩니다.

**Response** `204 No Content`

---

## 날짜 (Day)

### Day 추가
`POST /api/itineraries/{itineraryId}/days`

**Request Body**
```json
{
  "dayNumber": 1,
  "date": "2026-07-01"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| dayNumber | Integer | ✅ | 1 이상, 같은 일정 내 중복 불가 |
| date | LocalDate | ❌ | `yyyy-MM-dd` |

**Response** `201 Created`
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": "66bec83a-b4b1-49d5-8d0d-5eb1c3447795",
    "dayNumber": 1,
    "date": "2026-07-01",
    "items": []
  }
}
```

---

### Day 삭제
`DELETE /api/itineraries/{itineraryId}/days/{dayId}`

하위 Item 모두 cascade 삭제됩니다.

**Response** `204 No Content`

---

## 장소 항목 (Item)

### Item 추가
`POST /api/itineraries/{itineraryId}/days/{dayId}/items`

**Request Body**
```json
{
  "spotId": "5f66f31b-1cd0-4bd5-905d-28741f9a4c5d",
  "orderIndex": 0,
  "arrivalTime": "10:00:00",
  "durationMin": 120,
  "travelMode": "transit",
  "travelTimeMin": 20,
  "memo": "점심 먹고 이동"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| spotId | UUID | ✅ | 관광지 ID (`tour_spots.id`) |
| orderIndex | Integer | ✅ | 0부터 시작하는 순서 |
| arrivalTime | LocalTime | ❌ | `HH:mm:ss` |
| durationMin | Integer | ❌ | 체류 시간(분) |
| travelMode | String | ❌ | `walk` / `transit` / `taxi` |
| travelTimeMin | Integer | ❌ | 이동 시간(분) |
| memo | String | ❌ | 메모 |

**Response** `201 Created`
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": "80ad1a41-73b4-4156-8161-4ce664f79560",
    "orderIndex": 0,
    "spot": {
      "id": "5f66f31b-1cd0-4bd5-905d-28741f9a4c5d",
      "name": "해운대 해수욕장",
      "address": "부산 해운대구",
      "lat": 35.1587000,
      "lng": 129.1604000,
      "thumbnailUrl": null
    },
    "arrivalTime": "10:00:00",
    "durationMin": 120,
    "travelMode": "transit",
    "travelTimeMin": 20,
    "memo": "점심 먹고 이동"
  }
}
```

---

### Item 수정
`PATCH /api/itineraries/{itineraryId}/days/{dayId}/items/{itemId}`

실시간 편집 시 순서 변경, 시간 수정 등에 사용합니다.

**Request Body**
```json
{
  "orderIndex": 1,
  "arrivalTime": "11:00:00",
  "durationMin": 90,
  "travelMode": "walk",
  "travelTimeMin": null,
  "memo": "천천히 걷기"
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| orderIndex | Integer | ✅ | 변경할 순서 |
| arrivalTime | LocalTime | ❌ | `HH:mm:ss` |
| durationMin | Integer | ❌ | 체류 시간(분) |
| travelMode | String | ❌ | `walk` / `transit` / `taxi` |
| travelTimeMin | Integer | ❌ | 이동 시간(분) |
| memo | String | ❌ | 메모 |

**Response** `200 OK` — 수정된 Item 반환

---

### Item 삭제
`DELETE /api/itineraries/{itineraryId}/days/{dayId}/items/{itemId}`

**Response** `204 No Content`
