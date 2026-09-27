package com.bujirun.bujirun.global.exception;

// 인증(누구인지)은 됐지만 권한(그 대상에 접근할 자격)이 없을 때. IllegalArgumentException은
// 이미 400(잘못된 요청)에 쓰이고 있어서, "요청 자체는 유효하지만 이 리소스는 못 건드림"을
// 구분해서 표현해야 하는 곳(예: /api/internal/** 의 actorUserId 권한 체크)에 사용한다.
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
