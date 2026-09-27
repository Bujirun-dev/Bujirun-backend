package com.bujirun.bujirun.global.response;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }

    // 실패 응답이지만 호출부가 바로 쓸 수 있는 데이터를 함께 실어야 할 때(예: 낙관적 락
    // 충돌 409에 서버의 최신 상태를 함께 돌려줘 클라이언트가 추가 조회 없이 reconcile하게 함).
    public static <T> ApiResponse<T> fail(String message, T data) {
        return new ApiResponse<>(false, message, data);
    }
}