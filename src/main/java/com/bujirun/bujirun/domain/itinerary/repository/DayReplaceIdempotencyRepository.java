package com.bujirun.bujirun.domain.itinerary.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.UUID;

// day 전체교체(ReplaceDayItems) 요청의 멱등키 저장소. 같은 operationId로 온 요청은
// 실제 처리 없이 캐시된 응답(JSON)을 그대로 돌려준다 — 여러 클라이언트가 같은 논리적
// 편집을 동시에 재전송해도 실제로는 한 번만 반영되게 하기 위함.
//
// find() 후 save()하는 "확인 후 처리" 방식은 그 자체로 레이스다 — 여러 요청이 거의 동시에
// find()를 통과해버리면(아직 아무도 save() 안 한 시점) 전부 독립적으로 재처리해버린다
// (실제로 로컬에서 5개 동시요청 보내서 5개 다른 결과가 나오는 걸로 재현 확인, 2026-09-16).
// claim()으로 Redis SETNX를 써서 "먼저 선점한 요청 하나만 처리, 나머지는 그 결과를
// 기다렸다가 그대로 받는" 방식으로 바꿈.
@Repository
@RequiredArgsConstructor
public class DayReplaceIdempotencyRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "day-replace:";
    private static final String PROCESSING_MARKER = "__PROCESSING__";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final long POLL_INTERVAL_MS = 100;

    // 이 operationId를 처리할 권리를 선점한다. true면 이 호출자가 실제로 처리하고 save()까지
    // 책임진다. false면 다른 요청이 먼저(또는 이미 완료해) 선점한 것이므로 waitForResult()로
    // 그 결과를 기다려야 한다.
    public boolean claim(UUID operationId) {
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX + operationId, PROCESSING_MARKER, TTL);
        return Boolean.TRUE.equals(claimed);
    }

    public void save(UUID operationId, String responseJson) {
        redisTemplate.opsForValue().set(PREFIX + operationId, responseJson, TTL);
    }

    // 선점자가 결과를 못 남기고 끝났을 때(예외/롤백) 선점 표시를 푼다. 안 풀면 TTL(10분)
    // 내내 같은 operationId의 재시도가 전부 waitForResult에서 POLL_TIMEOUT만큼 기다린 뒤
    // 폴백하게 되어, node-yjs(요청 타임아웃)가 매번 끊고 다시 보내는 실패 루프가 된다
    // (2026-09-28 운영에서 10분간 실제 발생). 이미 결과가 저장된 키는 지우지 않는다.
    public void release(UUID operationId) {
        String key = PREFIX + operationId;
        if (PROCESSING_MARKER.equals(redisTemplate.opsForValue().get(key))) {
            redisTemplate.delete(key);
        }
    }

    // 이미 완료된 결과면 즉시 반환. 아직 처리 중(PROCESSING_MARKER)이면 최대 POLL_TIMEOUT까지
    // 짧은 간격으로 재조회해 기다린다. 그래도 안 끝나면 null(호출부가 직접 처리하도록 폴백 —
    // 선점자가 죽었을 가능성에 대비한 안전장치, DB 행 잠금이 있어 중복 처리돼도 데이터는
    // 안전하다. 다만 흔치 않은 경로다).
    public String waitForResult(UUID operationId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            String value = redisTemplate.opsForValue().get(PREFIX + operationId);
            if (value == null) return null;
            if (!PROCESSING_MARKER.equals(value)) return value;
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
