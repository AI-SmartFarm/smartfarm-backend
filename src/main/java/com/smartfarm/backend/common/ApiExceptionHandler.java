package com.smartfarm.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 요청 오류를 {"error": "..."} 형식으로 통일한다. 시뮬레이터는 400을 받으면 로그만 남긴다(API-001). */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(BadRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError badRequest(BadRequestException e) {
		return new ApiError(e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError invalid(MethodArgumentNotValidException e) {
		String field = e.getBindingResult().getFieldError() == null ? "request"
				: e.getBindingResult().getFieldError().getField();
		return new ApiError("invalid field: " + field);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	ApiError unreadable(HttpMessageNotReadableException e) {
		// 시뮬레이터 형식이 바뀌었을 때 원인을 찾을 수 있게 서버 로그에는 남기고, 응답에는 내부 정보를 싣지 않는다.
		log.warn("요청 본문을 읽지 못함: {}", e.getMostSpecificCause().getMessage());
		return new ApiError("malformed request body");
	}
}
