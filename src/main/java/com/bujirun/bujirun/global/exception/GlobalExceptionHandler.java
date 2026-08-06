package com.bujirun.bujirun.global.exception;

import com.bujirun.bujirun.domain.auth.exception.DuplicateNicknameException;
import com.bujirun.bujirun.domain.itinerary.generate.exception.OpenAiApiException;
import com.bujirun.bujirun.domain.itinerary.generate.exception.OpenAiRateLimitException;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(404).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
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

    // DB 유니크 제약 위반의 최종 방어선(예: 동시 요청이 애플리케이션 레벨 중복 체크를 함께 통과한 경우).
    // 원인을 특정할 수 없는 제약 위반도 있으므로 메시지는 범용으로 유지.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(ApiResponse.fail("이미 존재하거나 중복된 데이터입니다."));
    }
}