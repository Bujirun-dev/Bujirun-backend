-- V35: 영수증 발행(여행 기록 작성) 유도 팝업의 "다시 묻지 않음" 응답 저장.
-- 완료된 일정 중 아직 여행 기록이 없는 것에 대해 프론트가 영수증 발행 팝업을 띄우는데,
-- 사용자가 "다시 묻지 않음"을 선택하면 해당 (user, itinerary) 조합을 여기 기록해
-- 이후 그 일정에 대해서는 팝업을 띄우지 않는다.
-- (기존 프론트 localStorage 임시 처리(skippedReviews.ts)를 대체 — 기기 간 동기화됨)

CREATE TABLE receipt_prompt_dismissals (
    user_id       UUID       NOT NULL REFERENCES users(id),
    itinerary_id  UUID       NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
    created_at    TIMESTAMP  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, itinerary_id)
);

CREATE INDEX idx_receipt_prompt_dismissals_itinerary ON receipt_prompt_dismissals(itinerary_id);
