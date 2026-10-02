package com.smartfarm.backend.common;

/** 모든 에러 응답 본문. API-001·004 명세의 {"error": "..."} 형식과 맞춘다. */
public record ApiError(String error) {
}
