package com.douzone.server.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum ErrorCode {

    //EMP
    EMP_NOT_FOUND(404, "사원을 찾을 수 없습니다."), EMP_ALREADY_EXIST(400, "이미 존재하는 사원 입니다."),
    //ROOM
    RES_NOT_FOUND(404, "예약을 찾을 수 없습니다."),
    //500
    INTERNAL_SERVER_ERROR(500, "내부 서버 오류 입니다."),

    //400
    INVALID_INPUT_VALUE(400, "유효하지 않은 입력값입니다."),
    TYPE_MISMATCH(400, "타입이 맞지 않습니다."),
    MISSING_REQUEST_PARAMETER(400, "요청 파라미터를 잃어버렸습니다."),
    METHOD_NOT_ALLOWED(400, "허락되지 않은 메소드 입니다."),

    //end point
    MISS_MATCH_ENDPOINT(404, "해당 end point는 요청 할 수 없습니다."),

    IMG_NOT_FOUND(404, "이미지 파일을 찾을 수 없습니다."),
    
    MAX_UPLOAD_SIZE_EXCEEDED(400, "파일 용량이 초과하였습니다.");

    private final int status;
    private final String message;
}
