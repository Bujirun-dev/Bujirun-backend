package com.bujirun.bujirun.global.exception;

import com.bujirun.bujirun.domain.auth.exception.DuplicateNicknameException;
import com.bujirun.bujirun.domain.itinerary.dto.response.ItineraryDayResponse;
import com.bujirun.bujirun.domain.itinerary.exception.DayVersionConflictException;
import com.bujirun.bujirun.domain.itinerary.generate.exception.OpenAiApiException;
import com.bujirun.bujirun.domain.itinerary.generate.exception.OpenAiRateLimitException;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(404).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e, HttpServletRequest request) {
        logHandled(400, e, request);
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(403).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException e, HttpServletRequest request) {
        logHandled(409, e, request);
        return ResponseEntity.status(409).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
        log.warn("[handled 400] {} {} - {}", request.getMethod(), request.getRequestURI(), message);
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getParameterName() + " 파라미터가 필요합니다"));
    }

    // @Validated + @RequestParam/@PathVariable에 붙인 @Size 등이 실패했을 때(예: 닉네임 중복확인 API)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(OpenAiRateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAiRateLimit(OpenAiRateLimitException e) {
        return ResponseEntity.status(429)
                .body(ApiResponse.fail("AI 일정 생성 요청이 몰려 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(OpenAiApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleOpenAiApi(OpenAiApiException e) {
        return ResponseEntity.status(502)
                .body(ApiResponse.fail("AI 일정 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(DuplicateNicknameException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateNickname(DuplicateNicknameException e) {
        return ResponseEntity.status(409).body(ApiResponse.fail(e.getMessage()));
    }

    // 낙관적 락 충돌. 서버가 이미 들고 있는 최신 day 상태를 데이터로 함께 돌려줘, 프론트가
    // 별도 조회 없이 바로 로컬 상태를 이 값으로 되돌리고(reconcile) 재시도할 수 있게 한다.
    @ExceptionHandler(DayVersionConflictException.class)
    public ResponseEntity<ApiResponse<ItineraryDayResponse>> handleDayVersionConflict(DayVersionConflictException e) {
        return ResponseEntity.status(409).body(ApiResponse.fail(e.getMessage(), e.getCurrentDay()));
    }

    // DB 유니크 제약 위반의 최종 방어선(예: 동시 요청이 애플리케이션 레벨 중복 체크를 함께 통과한 경우).
    // 원인을 특정할 수 없는 제약 위반도 있으므로 메시지는 범용으로 유지.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e,
                                                                          HttpServletRequest request) {
        logHandled(409, e, request);
        return ResponseEntity.status(409).body(ApiResponse.fail("이미 존재하거나 중복된 데이터입니다."));
    }

    // 4xx로 처리한 예외는 그동안 로그가 전혀 남지 않아, 클라이언트가 자동 재시도로 넘어가면
    // 원인을 추적할 수 없었다(2026-09-29 그룹 일정 생성 대기 요청 400). 어느 요청에서 어떤
    // 코드가 던졌는지만 한 줄로 남긴다 — 스택 전체는 정상 흐름에서도 나올 수 있어 과하다.
    private void logHandled(int status, Exception e, HttpServletRequest request) {
        StackTraceElement[] trace = e.getStackTrace();
        String origin = trace.length > 0 ? trace[0].toString() : "unknown";
        // 라이브러리(Jackson, Hibernate 등) 안에서 던져졌으면 trace[0]만으론 우리 코드 어디서
        // 불렀는지 모른다 — 가장 가까운 앱 코드 위치를 함께 남긴다.
        for (StackTraceElement frame : trace) {
            if (frame.getClassName().startsWith("com.bujirun.")) {
                if (frame != trace[0]) origin = frame + " via " + origin;
                break;
            }
        }
        log.warn("[handled {}] {} {} - {}: {} (at {})", status, request.getMethod(), request.getRequestURI(),
                e.getClass().getSimpleName(), e.getMessage(), origin);
    }
}