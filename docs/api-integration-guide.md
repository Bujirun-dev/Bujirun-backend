# 프론트엔드 연동 가이드

로그인부터 일정 편집, 여행 로그까지 프론트에서 실제로 호출해야 하는 순서대로 정리한 문서입니다.
범위: **로그인/회원가입 → 유저 프로필 → 이미지 업로드 → 일정(실시간 편집) → 여행 로그**

Base URL: `https://api.bujirun.store` (로컬: `http://localhost:8080`)

---

## 0. 공통

### 응답 형식
모든 API는 아래 포맷으로 감싸서 응답합니다.
```json
{ "success": true, "message": "OK", "data": { } }
```
실패 시 `success: false`, `data: null`, `message`에 에러 문구.

| HTTP 상태 | 상황 |
|-----------|------|
| 400 | 요청 값 검증 실패 |
| 401/403 | 인증 필요 / 토큰 없음·무효 |
| 404 | 리소스 없음 |
| 500 | 서버 내부 오류 |

### 인증 헤더
`/api/auth/**` 를 제외한 모든 API는 로그인 필요합니다.
```
Authorization: Bearer {accessToken}
```
accessToken은 30분, refreshToken(2주)은 로그인 시 HttpOnly 쿠키로 자동 저장되므로 프론트가 직접 다루지 않습니다 (요청 시 쿠키 자동 첨부, `credentials: 'include'` 필요).

---

## 1. 로그인 / 회원가입 (카카오 전용)

이 서비스는 카카오 로그인만 지원합니다. 별도의 이메일 회원가입 화면은 없고, **카카오 로그인 = 회원가입**입니다.

### 1-1. 카카오 로그인
`POST /api/auth/kakao/token?code={인가코드}`

카카오 인가 코드(프론트에서 카카오 SDK로 받은 값)를 그대로 쿼리 파라미터로 전달합니다.

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "isNewUser": true
  }
}
```

| 필드 | 설명 |
|------|------|
| accessToken | 이후 모든 요청의 `Authorization` 헤더에 사용 |
| isNewUser | **true면 신규 가입자** — 닉네임/프로필사진이 카카오 기본값으로 자동 채워진 상태. false면 기존 회원, 바로 홈으로 |

동시에 `refresh_token`이 HttpOnly 쿠키로 내려갑니다 (`Secure` 플래그는 아직 안 붙어있어 별도 백엔드 보완 예정이니 참고만 해두세요).

**프론트 분기 처리:**
- `isNewUser: true` → 닉네임/프로필사진 설정 화면으로 이동 → 완료 시 [2. 유저 프로필](#2-유저-프로필) 의 PATCH 호출
- `isNewUser: false` → 바로 홈 화면

### 1-2. 토큰 재발급
`POST /api/auth/reissue`

accessToken이 만료되면 (401) 호출. 쿠키의 refresh_token을 서버가 자동으로 읽습니다 (요청 바디 없음, `credentials: 'include'` 필수).

**Response** `200 OK`
```json
{ "success": true, "data": { "accessToken": "새 토큰", "tokenType": "Bearer" } }
```

### 1-3. 로그아웃
`POST /api/auth/logout`

Redis의 refresh token 삭제 + 쿠키 만료. 바디 없음.

---

## 2. 유저 프로필

### 2-1. 내 프로필 조회
`GET /api/users/me`

```json
{
  "success": true,
  "data": {
    "id": "11111111-...",
    "nickname": "홍길동",
    "profileImageUrl": "https://bujirun-storage.s3.ap-northeast-2.amazonaws.com/uploads/.../a.jpg",
    "email": "user@kakao.com"
  }
}
```

### 2-2. 닉네임 / 프로필사진 수정
`PATCH /api/users/me`

```json
{ "nickname": "새닉네임", "profileImageUrl": "https://.../b.jpg" }
```

| 필드 | 필수 | 설명 |
|------|------|------|
| nickname | ❌ | 보낼 경우 1~50자. 빈 문자열/공백만 있으면 400 |
| profileImageUrl | ❌ | [3. 이미지 업로드](#3-이미지-업로드-s3) 로 받은 `publicUrl`을 그대로 넣기 |

두 필드 다 선택이라 하나만 보내도 됩니다 (부분 수정). `null`을 보낸 필드는 변경하지 않습니다.

---

## 3. 이미지 업로드 (S3)

프로필사진, 여행 로그 사진 전부 이 방식 하나로 통일되어 있습니다. **서버를 거치지 않고 클라이언트가 S3에 직접 업로드**하는 구조입니다.

### 3-1. 업로드 URL 발급
`POST /api/uploads/presign`

```json
{ "contentType": "image/jpeg" }
```
`contentType`은 `image/jpeg`, `image/png`, `image/webp`, `image/gif` 중 하나만 허용.

**Response** `200 OK`
```json
{
  "success": true,
  "data": {
    "uploadUrl": "https://bujirun-storage.s3.../uploads/{userId}/{uuid}.jpg?X-Amz-...",
    "publicUrl": "https://bujirun-storage.s3.ap-northeast-2.amazonaws.com/uploads/{userId}/{uuid}.jpg"
  }
}
```
`uploadUrl`은 발급 후 **10분간만 유효**합니다.

### 3-2. 실제 업로드 (S3로 직접)
`uploadUrl`로 이미지 원본 바이트를 **PUT** — 우리 서버 API가 아니라 S3 주소로 직접 요청합니다.
```
PUT {uploadUrl}
Content-Type: image/jpeg   (presign 요청 때 넣은 값과 동일해야 함)
Body: 이미지 파일 원본 바이너리
```
`Authorization` 헤더 불필요 (presign URL 자체에 서명이 들어있음).

### 3-3. 완료 후
`publicUrl`을 아래처럼 그대로 사용:
- 프로필사진 → `PATCH /api/users/me`의 `profileImageUrl`
- 여행 로그 사진 → `POST /api/logs/{logId}/items/{itemId}/photos`의 `photoUrl`

즉 전체 사진 등록 흐름은 **presign → S3 PUT → publicUrl을 기존 API에 넣기**, 3단계입니다.

---

## 4. 일정 (Itinerary)

> 실제 여러 명이 동시에 편집하는 **실시간 협업(커서 공유 등)은 이 백엔드가 아니라 별도의 node-yjs 서버(WebSocket)**가 담당합니다. 아래는 일정 데이터 자체를 만들고 저장하는 REST API이며, 협업 소켓 프로토콜은 이 문서 범위 밖입니다.

모든 요청은 `Authorization` 헤더의 유저를 소유자로 사용합니다 (`userId`를 body/query로 넘기지 않습니다 — 넘겨도 무시됨).

### 4-1. 일정 생성
`POST /api/itineraries`
```json
{ "planType": "A", "title": "부산 2박 3일", "startAt": "2026-08-01", "endAt": "2026-08-03" }
```
`planType`은 `A`/`B`/`C` (기본 `A`), 나머지 전부 선택.

### 4-2. 내 일정 목록
`GET /api/itineraries` → `ItinerarySummaryResponse` 배열 (id, title, planType, status, createdAt, updatedAt)

### 4-3. 일정 상세
`GET /api/itineraries/{id}` → Day/Item까지 전부 포함한 트리 구조

### 4-4. 일정 수정 / 삭제
`PATCH /api/itineraries/{id}` — `title`/`startAt`/`endAt`/`status`(`draft`|`confirmed`) 중 보낸 필드만 반영
`DELETE /api/itineraries/{id}` — Day/Item cascade 삭제

### 4-5. Day 추가/삭제
`POST /api/itineraries/{itineraryId}/days` — `{ "dayNumber": 1, "date": "2026-08-01" }`
`DELETE /api/itineraries/{itineraryId}/days/{dayId}`

### 4-6. Item 추가/수정/삭제 (실제 "편집"은 대부분 여기)
`POST /api/itineraries/{itineraryId}/days/{dayId}/items`
```json
{
  "spotId": "5f66f31b-...",
  "orderIndex": 0,
  "arrivalTime": "10:00:00",
  "durationMin": 120,
  "travelMode": "transit",
  "travelTimeMin": 20,
  "memo": "점심 먹고 이동"
}
```
`travelMode`: `walk`/`transit`/`taxi`.

`PATCH /api/itineraries/{itineraryId}/days/{dayId}/items/{itemId}` — 순서 변경, 시간 수정 등 편집할 때마다 호출 (동일 스키마, 전부 선택)
`DELETE /api/itineraries/{itineraryId}/days/{dayId}/items/{itemId}`

> 참고: 저장소에 있던 기존 `docs/itinerary-api.md`는 JWT 인증 도입 이전 버전이라 `userId`를 body/query에 직접 넣는 것으로 되어 있는데, 지금은 위처럼 `Authorization` 헤더로만 유저를 식별합니다. 상세 스펙은 위 내용을 기준으로 봐주세요.

---

## 5. 여행 로그 (Travel Log)

일정(Itinerary)을 실제로 다녀온 뒤 사진/후기를 남기는 기능. 한 일정당 로그 1개.

### 5-1. 로그 생성
`POST /api/logs`
```json
{ "itineraryId": "68acbcac-...", "isPublic": false, "mood": 2, "theme": "휴식" }
```
본인 소유 일정이 아니면 403 대신 400(권한 없음 메시지)으로 응답.

| 필드 | 필수 | 설명 |
|------|------|------|
| mood | ❌ | "여행 어떠셨나요?" 화면의 이모티콘 선택 인덱스. **매핑표(숫자 ↔ 이모지)는 프론트에서만 관리** — 서버는 숫자를 그대로 저장/반환만 함 |
| theme | ❌ | 여행 키워드 텍스트(예: "휴식"), 최대 50자 |

영수증 이미지 자체는 프론트에서 이 데이터(+ 아래 방문 인증 사진)로 렌더링하는 것이라 백엔드는 별도 API 없음.

### 5-2. 로그 상세 조회
`GET /api/logs/{id}` — 비공개(`isPublic: false`) 로그는 작성자 본인만 조회 가능

### 5-3. 로그 목록
- 내 로그: `GET /api/logs/me`
- 공개 피드: `GET /api/logs/public?category={카테고리}&sort=latest|popular`
- 특정 관광지 기준: `GET /api/logs/spot/{spotId}`

목록 응답에는 `authorNickname`이 포함되어 있어 [2. 유저 프로필](#2-유저-프로필)에서 설정한 닉네임이 그대로 노출됩니다.

### 5-4. 로그 수정 / 삭제
`PATCH /api/logs/{id}` — `{ "isPublic": true }` (공개 여부만 변경 가능)
`DELETE /api/logs/{id}`

### 5-5. 사진 등록/대표사진/삭제
사진은 Day의 Item 단위로 붙습니다 (`itemId` = 일정의 ItineraryItem id).

`POST /api/logs/{logId}/items/{itemId}/photos`
```json
{ "photoUrl": "https://bujirun-storage.s3.../uploads/.../c.jpg" }
```
→ [3. 이미지 업로드](#3-이미지-업로드-s3)에서 받은 `publicUrl`을 그대로 넣기.

`PATCH /api/logs/{logId}/items/{itemId}/photos/{photoId}/representative` — 대표(썸네일) 사진 지정. 로그 목록의 `thumbnailPhotoUrl`에 반영됨.
`DELETE /api/logs/{logId}/items/{itemId}/photos/{photoId}`

### 5-6. 해시태그
`POST /api/logs/{logId}/items/{itemId}/hashtags` — `{ "tag": "노을맛집" }` (최대 50자)
`DELETE /api/logs/{logId}/items/{itemId}/hashtags/{hashtagId}`

---

## 6. 방문 인증 (Visit)

관광지 상세 페이지 등에서 "인증하기"를 누르면 GPS로 실제 방문 여부를 확인하는 기능. **관광지 + 사용자 단위**로 기록되고, 특정 일정(itinerary)에는 연결되지 않습니다.

### 6-1. GPS 인증
`POST /api/visits`
```json
{ "tourSpotId": "5f66f31b-...", "gpsLat": 35.1587, "gpsLng": 129.1604 }
```

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "visitId": "b2f1...",
    "verified": true,
    "distanceMeters": 42.3,
    "firstVisit": true
  }
}
```

| 필드 | 설명 |
|------|------|
| verified | 관광지 좌표와의 거리가 허용 반경(카테고리별로 100~500m) 이내면 `true`. `false`여도 시도 기록은 저장됨 — "인증 실패" 안내만 하고 사진 촬영 단계로는 넘어가지 않으면 됨 |
| firstVisit | 이 사용자가 이 관광지를 **처음으로 인증 성공**한 경우 `true`, 이전에도 인증 성공한 적 있으면(재방문) `false`. "새로운 관광지 +1" vs "경험치 +1" 모달 분기에 그대로 사용 |

`verified: true`일 때 해당 관광지가 도감 수집 대상(`isCollection`)이면 자동으로 도감에도 등록됩니다 (`GET /api/spots/search` 응답의 `collected` 필드에 반영).

### 6-2. 인증 사진 첨부
GPS 인증(6-1) 성공 후 사진을 찍고 다시 "인증하기"를 누르는 두 번째 단계에서 호출합니다. 한 방문 기록에 여러 장 첨부 가능(제한 없음).

`POST /api/visits/{visitId}/photos`
```json
{ "photoUrl": "https://bujirun-storage.s3.../uploads/.../d.jpg" }
```
→ [3. 이미지 업로드](#3-이미지-업로드-s3)에서 받은 `publicUrl`을 그대로 사용. `visitId`는 6-1 응답의 `visitId`.

`verified: false`인 방문 기록이나 본인 소유가 아닌 방문 기록에는 400 에러.

### 6-3. 내 방문 인증 이력
`GET /api/visits` — 로그인 사용자의 인증 시도 이력을 최신순으로 반환 (성공/실패 전부 포함)

```json
{
  "success": true,
  "data": [
    {
      "visitId": "b2f1...",
      "spotId": "5f66f31b-...",
      "spotName": "속도 해수욕장",
      "spotThumbnailUrl": "https://...",
      "verified": true,
      "distanceMeters": 42.3,
      "visitedAt": "2026-07-11T14:20:00",
      "photoUrls": ["https://.../d.jpg", "https://.../e.jpg"]
    }
  ]
}
```

### 6-4. 관광지/일정 조회에 포함된 인증 여부
별도 API 호출 없이, 기존 조회 API 응답에 인증 여부/도감 수집 여부 필드가 이미 들어갑니다. `GET /api/spots/search`, `GET /api/spots/{spotId}`, `GET /api/itineraries/{id}`(각 Day → Item → `spot`) 셋 다 JSON 키는 `visited`/`collected`로 통일되어 있습니다 (2026-07-12에 `/api/itineraries/{id}`만 `isVisited`/`isCollected`로 다르게 나가던 것을 통일함).

일정에서 관광지를 탭해서 인증 화면으로 넘어갈 때, 이미 이 값으로 "이미 인증한 관광지"인지 미리 알 수 있습니다 (`firstVisit`과 별개로, 인증 시도 전에 미리 안내하고 싶을 때 사용).

`collected`는 인증(`POST /api/visits`) 성공 시점에 같은 트랜잭션에서 즉시 반영됩니다 — 다만 인증 API 응답(`VisitResponse`) 자체엔 `collected`가 안 실려 있으므로, 값을 새로 받으려면 인증 후 위 조회 API들을 다시 호출(refetch)해야 합니다.

### 6-5. 사진 저장 전체 연동 흐름

GPS 인증과 사진 첨부는 **서로 독립된 두 번의 API 호출**입니다. 사진 URL이 인증 성공 여부를 결정하거나 저장하는 데 관여하지 않습니다 — `verified`는 6-1에서 GPS만으로 이미 확정·저장됩니다.

```
1) "인증하기"(1차 클릭)
   → GPS 좌표 획득
   → POST /api/visits { tourSpotId, gpsLat, gpsLng }
   → { visitId, verified, firstVisit }
   ★ 이 시점에 인증 성공/실패는 이미 DB에 저장 완료 (사진 없이도)

2) verified === false → "인증 실패" 안내하고 종료 (사진 단계로 넘어가지 않음)
   verified === true  → 카메라 화면으로 이동

3) 사진 촬영 → "인증하기"(2차 클릭)
   a. POST /api/uploads/presign { contentType: "image/jpeg" } → { uploadUrl, publicUrl }
   b. PUT {uploadUrl}  ← 우리 서버가 아니라 S3로 직접 이미지 바이트 전송
   c. POST /api/visits/{visitId}/photos { photoUrl: publicUrl }
      visitId는 1)에서 받은 값을 그대로 사용

4) 완료 모달 — 1)에서 받은 firstVisit으로 "새 관광지"/"경험치" 분기 표시
```

**조회할 때는 URL을 다시 서버에 물어볼 필요가 없습니다.** `GET /api/visits`가 `photoUrls` 배열에 완성된 URL 문자열을 그대로 담아 내려주므로, 받은 문자열을 이미지 컴포넌트(`<Image src={photoUrl}>`)에 그대로 꽂아 쓰면 됩니다. S3 버킷이 공개 읽기(public-read)라 URL만 있으면 바로 로드됩니다 — 프로필사진(`profileImageUrl`), 일정 썸네일(`thumbnailUrl`)과 완전히 같은 패턴입니다.

즉 전 과정을 한 문장으로: **presign → S3 PUT → 결과 URL 문자열만 우리 서버에 저장 → 조회 시 그 문자열을 그대로 돌려받아 렌더링.**

---

## 전체 흐름 요약

```
카카오 로그인 (1-1)
  └─ isNewUser=true → 프로필 설정 화면
        └─ 이미지 선택 → presign(3-1) → S3 PUT(3-2) → PATCH /api/users/me(2-2)
  └─ isNewUser=false → 홈

홈 → 일정 생성/편집 (4) → 여행 다녀옴
  └─ 관광지 도착 → 인증하기 (6-1) → verified:true면 사진 촬영
        └─ presign(3-1) → S3 PUT(3-2) → POST .../photos(6-2)
  └─ 여행 종료 → 로그 생성(5-1, mood/theme 포함) → GET /api/logs/{id}(5-2)로 일정 전체 + 방문 사진(6-3) 모아서 영수증 렌더링(프론트)
  └─ 로그 사진 추가할 때마다: presign(3-1) → S3 PUT(3-2) → POST .../photos(5-5)
  └─ 완료 후 공개 전환: PATCH /api/logs/{id} { isPublic: true } → 공개 피드(5-3)에 노출
```
