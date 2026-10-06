# API 명세 초안 (백엔드)

- 작성: 김승윤 (백엔드) / 최초 2026-09-28, 수정 2026-10-02
- **백엔드 API의 기준 문서다.** 노션 API 명세와 시뮬레이터 디버그 키트는 참고 자료이고, 서로 다르면 이 문서에 맞춘다.
- API-001(환경 데이터 전송), API-004(작물 이미지 전송)의 요청 본문은 노현석 님 명세를 그대로 따르므로 여기서 다시 쓰지 않는다.
- 세부 필드와 에러 코드는 구현하면서 확정한다. 구현이 끝난 API는 "구현 상태"에 표시한다.
- 관련 문서: `docs/design/jwt-auth.md`, `docs/design/erd-spec.md`

## API 목록과 구현 상태

번호는 노션 API 명세 목록(2026-10-02 기준)에 맞췄다. 이 문서의 이전 판에서 번호가 바뀐 것은 비고에 적었다.

| 번호 | API | 방향 | 인증 | 구현 상태 | 비고 |
|---|---|---|---|---|---|
| API-001 | 환경 데이터 전송 | 시뮬레이터 → 백엔드 | JWT | **구현·배포** | 1분에 1건 저장 |
| API-002 | 환경 및 장치 상태 조회 | 모바일 → 백엔드 | 없음 | 미구현 | |
| API-003 | 장치 제어 명령 전달 | 백엔드 → 시뮬레이터 | JWT | **구현·배포** | 조회 + 결과 보고(ACK). 자동제어가 명령을 만든다 |
| API-004 | 작물 이미지 전송 | 시뮬레이터 → 백엔드 | JWT | 구현 (PR #5, merge 전) | 김우주. 사진 저장 정책은 미정 |
| API-005 | 병해충 진단 요청 | 백엔드 → AI | JWT (현재 API 키) | **호출 코드 구현** (김우주). 사진 수신 시 자동 호출은 PR #5 | 중증도(API-006)가 응답에 함께 온다 |
| API-006 | 중증도 판정 | AI → 백엔드 | – | API-005 응답에 포함 | 별도 호출 없음 |
| API-007 | 제어 로그·최근 사진 조회 | 모바일 → 백엔드 | 없음 | 미구현 | |
| API-008 | 병해충 진단 이력 조회 | 모바일 → 백엔드 | 없음 | 구현 (PR #5, merge 전) | 김우주. `responses`(자동 대응)는 아직 빈 배열 |
| API-009 | 기기 토큰 발급 (JWT) | 시뮬레이터 → 백엔드 | 기기 ID + 비밀값 | **구현·배포** | 이전 판의 API-008 |
| API-010 | 푸시 알림 토큰 등록 | 모바일 → 백엔드 | 없음 | 미구현 | 이전 판의 API-009 |

## 0. 공통 규칙

| 항목 | 내용 |
|---|---|
| Base URL | 테스트 서버 `https://<터널 주소>` (현재 임시 주소, 바뀌면 공유) |
| 데이터 형식 | JSON (`Content-Type: application/json; charset=utf-8`), 필드 이름은 camelCase |
| 시각 형식 | 백엔드가 내보내는 시각은 ISO-8601 한국 시간 (예: `2026-10-01T14:30:00+09:00`) |
| 인증 | **서버 간 통신에만 JWT를 쓴다.** 시뮬레이터 → 백엔드(`Authorization: Bearer <accessToken>`, API-009에서 발급)와 백엔드 → AI 서버. 모바일 → 백엔드는 사용자 로그인이 없어 인증하지 않는다. 자세한 방식은 `docs/design/jwt-auth.md` |
| 에러 형식 | `{"error": "설명"}` (API-001·004와 같음) |
| 공통 상태 코드 | 200 성공 / 400 잘못된 요청 / 401 토큰 없음·만료 / 403 권한 없음(토큰의 농장 ≠ URL의 농장) / 404 없음 / 500 서버 오류 |
| 빠진 필드 | 요청 JSON에서 필드가 빠지면 `null`로 받는다. 백엔드가 쓰지 않는 필드는 무시한다 |

**JWT가 필요한 API의 공통 검증** (API-001, 003, 004)
1. 서명과 만료 시간 확인 → 실패하면 `401 {"error": "invalid or expired access token"}`
2. 토큰의 `farmId`와 URL의 `{farmId}` 비교 → 다르면 `403 {"error": "farm access denied"}`
3. 기기 상태가 `ACTIVE`인지 확인 → 아니면 `403 {"error": "gateway inactive"}`

---

## API-009 기기 토큰 발급 (JWT)

> 구현 상태: **구현·배포 완료** (2026-10-02). 9/29 회의에서 JWT 기기 인증이 김승윤 담당으로 정해졌다.
> 전달 필요: **노현석 님** — 시뮬레이터의 현재 로그인 요청(`POST /auth/login`, `{farmId, deviceId, secret}`)을 이 명세에 맞춰야 한다. 차이는 `docs/design/jwt-auth.md` 4장에 정리했다.

[API ID] API-009
[API 이름] 기기 토큰 발급
[담당] 김승윤
[방향] 시뮬레이터 → 백엔드
[Method] POST
[URL] `/api/v1/auth/token`
[설명] 등록된 기기가 ID와 비밀값으로 JWT를 발급받는다. 발급받은 토큰으로 API-001, 003, 004를 호출한다. 사용자 로그인이 아니라 기기 인증이다.
[인증] 없음 (이 API로 토큰을 받는다)

[Request Body]
```json
{
  "gatewayId": "SIM001",
  "secret": "개발 시 등록한 기기 비밀값"
}
```

| 필드명 | 타입 | 필수 | 설명 |
|---|---|---|---|
| gatewayId | String | O | 기기 ID |
| secret | String | O | 기기 비밀값. 서버에는 해시로만 저장된다 |

[Response] 200
```json
{
  "accessToken": "<발급된 JWT 문자열>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "farmId": "greenhouse-01"
}
```

| 필드명 | 타입 | 설명 |
|---|---|---|
| accessToken | String | JWT. 내용: `iss`, `sub`=gatewayId, `farmId`, `iat`, `exp` |
| tokenType | String | 항상 `Bearer` |
| expiresIn | Number | 유효 시간(초). 1시간 |
| farmId | String | 이 기기가 속한 농장. 이후 API URL에 쓴다 |

[HTTP Status]
- 200: 발급 성공
- 400: 필드 누락 `{"error": "invalid field: <필드명>"}`
- 401: ID 또는 비밀값이 틀림 `{"error": "invalid credentials"}`
- 403: 비활성 기기 `{"error": "gateway inactive"}`

[비고]
- 재발급 토큰(refresh token)은 두지 않는다. 토큰이 만료돼 401을 받으면 이 API를 다시 호출한다. API-001 명세의 "토큰 버리고 재로그인 후 1회 재시도"와 같은 동작이다.
- 없는 기기와 틀린 비밀값은 같은 401로 응답한다. 기기 ID가 존재하는지 알 수 없게 하기 위해서다.
- 기기 비밀값은 서버 환경 변수(`SEED_GATEWAY_SECRET`)로만 관리하고, 시뮬레이터 담당자에게 개인 메시지로 전달한다.

---

## API-003 장치 제어 명령 전달

> 구현 상태: **구현·배포 완료** (PR #6, 2026-10-06). 가짜 Unity(디버그 키트)로 명령 수신 → `applied` ACK → `ACKED`까지 확인했다.

시뮬레이터에는 수신 서버가 없으므로, 백엔드가 명령을 대기열에 넣어 두고 시뮬레이터가 주기적으로 **가져가는(polling)** 방식이다. 명령은 백엔드의 자동제어(기능 명세 F-02)가 만든다.

### API-003-A 대기 중인 명령 조회

[API ID] API-003-A
[API 이름] 대기 중인 제어 명령 조회
[담당] 김승윤
[방향] 백엔드 → 시뮬레이터 (시뮬레이터가 요청)
[Method] GET
[URL] `/api/v1/farms/{farmId}/commands`
[설명] **결과 보고(ACK)를 받지 못한 명령을 모두** 내려준다. ACK가 유실될 수 있어, 받을 때까지 조회할 때마다 다시 내려준다.
[인증] JWT

[Response] 200
```json
{
  "commands": [
    {
      "id": "cmd_aa9c4d567057",
      "actuator": "circFan",
      "action": "on",
      "reason": "TEMP_HIGH",
      "createdAt": "2026-10-06T15:33:29.895914+09:00"
    }
  ]
}
```

| 필드명 | 타입 | 설명 |
|---|---|---|
| commands | Array | 대기 명령 목록. 없으면 빈 배열 `[]` |
| id | String | 명령 ID. 결과 보고(API-003-B)에 같은 값을 쓴다 |
| actuator | String | `circFan`(순환팬), `waterPump`(관수 펌프). API-001 `actuators` 이름과 같다 |
| action | String | `on` / `off` |
| reason | String | 명령 원인: `TEMP_HIGH`, `TEMP_NORMAL`, `SOIL_LOW`, `SOIL_ENOUGH` (병해 대응 `PEST_RESPONSE`는 3단계). 시뮬레이터 동작에는 영향이 없고 로그용이다 |
| createdAt | String | 명령 생성 시각 |

[HTTP Status]
- 200: 성공 (대기 명령이 없어도 200)
- 401 / 403: 공통 규칙 참고

### API-003-B 명령 처리 결과 보고 (ACK)

[API ID] API-003-B
[API 이름] 명령 처리 결과 보고
[담당] 김승윤
[방향] 시뮬레이터 → 백엔드
[Method] POST
[URL] `/api/v1/farms/{farmId}/commands/ack`
[인증] JWT

[Request Body]
```json
{ "id": "cmd_aa9c4d567057", "status": "applied" }
```

| 필드명 | 타입 | 필수 | 설명 |
|---|---|---|---|
| id | String | O | 처리한 명령 ID |
| status | String | O | `applied`(받아들임) / `rejected`(거절: 고장, 모르는 명령 등) |

[Response] 200 `{"ok": true}`

[HTTP Status]
- 200: 성공. 모르는 명령 ID여도 200 (시뮬레이터가 오래된 ACK를 다시 보낼 수 있다)
- 400: `status`가 `applied`/`rejected`가 아님
- 401 / 403: 공통 규칙 참고

[비고]
- 같은 명령에 ACK가 다시 와도 처음 결과를 유지한다. 만료된 뒤에 온 ACK는 상태를 바꾸지 않는다.

### 명령 생성 규칙 (백엔드 내부)
| 규칙 | 값 | 이유 |
|---|---|---|
| 판단 | 텔레메트리를 받을 때마다. 작물 기준값(`crop_profile`)과 비교, 켜는 기준과 끄는 기준이 다름(히스테리시스) | 저장 주기와 상관없이 제어가 늦어지지 않게 |
| 장치 현재 상태 | 시뮬레이터가 보고한 `actuators.circFan`, `waterPump` | 명령이 만료되거나 거절돼도 다음 텔레메트리에서 다시 판단하게 |
| 중복 방지 | 같은 장치에 결과를 기다리는 명령이 있으면 새로 만들지 않음 | |
| 쿨다운 | 같은 장치에 직전 명령 후 30초 안에는 새 명령 없음 (`CONTROL_COMMAND_COOLDOWN_SECONDS`) | 거절된 명령이 텔레메트리마다 반복되지 않게 |
| 만료 | 60초 안에 ACK가 없으면 `EXPIRED` (`CONTROL_COMMAND_TTL_SECONDS`) | 시뮬레이터가 오래 꺼졌다 켜졌을 때 묵은 명령이 실행되지 않게 |
| 기준값 없는 작물 | 제어하지 않음 | 잘못된 기준으로 제어하지 않게 |
| 가동 시간 | 병해 대응처럼 "60분 가동"이 필요하면 백엔드가 60분 뒤 `off`를 다시 넣는다(3단계) | 시뮬레이터 수정이 필요 없게 |

**시뮬레이터 현재 구현과의 차이** (노션 API-003, 디버그 키트 기준)

| 항목 | 이 명세 | 시뮬레이터 | 영향 |
|---|---|---|---|
| 조회·ACK 경로와 형식 | 위와 같음 | 같음 | 없음 (가짜 Unity로 확인) |
| 푸시(SSE) `/commands/stream` | 없음 | 기본으로 시도 | 404면 시뮬레이터가 스스로 폴링으로 전환한다 (확인함) |
| 명령 필드 | `id, actuator, action, reason, createdAt` | `value, deviceId, cellX, cellZ, issuedUtc`도 읽음 | 빠진 `value`는 0, 좌표는 -1(전체)로 본다. 모르는 필드는 무시한다 |
| 수집 주기 조절 | 미구현 | `actuator: "comm"` 명령 지원 | 수집 방안이 확정되면 이 명령으로 센서·사진 주기를 조절한다 |

---

## API-002 환경 및 장치 상태 조회

> 확인 필요: **수현 님** — 메인 대시보드와 온도·토양 수분 화면에 필요한 값이 빠지지 않았는지, 그래프 기간(1시간/24시간/7일)을 이대로 할지

### API-002-A 현재 상태

[API ID] API-002-A
[API 이름] 현재 농장 상태 조회
[담당] 김승윤
[방향] 백엔드 → 모바일 (앱이 요청)
[Method] GET
[URL] `/api/v1/farms/{farmId}/status`
[설명] 메인 대시보드의 현재 온도, 토양 수분, 장치 상태, 최근 병해 진단을 한 번에 조회한다.
[인증] 없음

[Response] 200
```json
{
  "farmId": "greenhouse-01",
  "updatedAt": "2026-10-01T14:30:00+09:00",
  "connected": true,
  "crop": { "species": "Tomato", "nameKo": "토마토", "stage": "Vegetative" },
  "temperature": { "value": 24.5, "targetMin": 22.0, "targetMax": 26.0, "state": "NORMAL" },
  "soilMoisture": { "value": 32.0, "targetMin": 30.0, "targetMax": 50.0, "state": "NORMAL" },
  "devices": { "circFan": false, "waterPump": false, "ventFan": false },
  "latestDiagnosis": {
    "diagnosisId": 12,
    "infected": false,
    "diseaseName": null,
    "diagnosedAt": "2026-10-01T14:00:00+09:00"
  }
}
```

| 필드명 | 타입 | 설명 |
|---|---|---|
| updatedAt | String | 마지막 텔레메트리 수신 시각 |
| connected | Boolean | 시뮬레이터가 최근(안: 30초 안)에 데이터를 보냈는지 |
| crop | Object | 대표 작물의 종류와 생육 단계 |
| temperature, soilMoisture | Object | 현재값, 적정 범위(작물 기준값), 상태 `NORMAL` / `HIGH` / `LOW`. 센서가 없으면 `value`가 `null` |
| devices | Object | 장치별 실제 작동 여부 (API-001 `actuators` 기준) |
| latestDiagnosis | Object | 가장 최근 AI 진단. 없으면 `null` |

[HTTP Status]
- 200: 성공
- 404: 없는 농장, 또는 아직 텔레메트리를 한 번도 받지 않음

### API-002-B 환경 변화 추이 (그래프)

[API ID] API-002-B
[API 이름] 환경 데이터 추이 조회
[담당] 김승윤
[방향] 백엔드 → 모바일
[Method] GET
[URL] `/api/v1/farms/{farmId}/telemetry?range=24h`
[설명] 온도·토양 수분 그래프에 쓸 시간별 데이터를 조회한다.
[인증] 없음

[Query]
| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| range | String | O | `1h` / `24h` / `7d` |

[Response] 200
```json
{
  "range": "24h",
  "intervalMinutes": 30,
  "points": [
    { "time": "2026-10-01T10:00:00+09:00", "temperature": 23.8, "soilMoisture": 41.0 },
    { "time": "2026-10-01T10:30:00+09:00", "temperature": 24.6, "soilMoisture": 38.5 }
  ]
}
```

[비고]
- 그래프가 너무 촘촘하지 않도록 백엔드가 구간 평균으로 줄여서 보낸다. 안: `1h`는 1분, `24h`는 30분, `7d`는 3시간 간격.

---

## API-007 제어 로그·최근 사진 조회

> 구현 상태: 미구현.
> 확인 필요: **수현 님** — 온도 로그, 토양 수분 로그, 내 작물 확인 화면에 필요한 값이 빠지지 않았는지. 노션 목록에서는 API-007이 빠져 있어 다시 넣어야 한다.

### API-007-A 제어 활동 로그

[API ID] API-007-A
[API 이름] 제어 활동 로그 조회
[담당] 김승윤
[방향] 백엔드 → 모바일
[Method] GET
[URL] `/api/v1/farms/{farmId}/control-logs?type=TEMP&size=20`
[설명] 온도 로그·토양 수분 로그 화면의 "활동 로그"를 조회한다.
[인증] 없음

[Query]
| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| type | String | X | `TEMP`(순환팬, 온도 원인) / `SOIL`(관수) / `PEST`(병해 대응). 없으면 전체 |
| size | Number | X | 개수, 기본 20 |
| before | String | X | 이 시각 이전 로그 (더 보기) |

[Response] 200
```json
{
  "logs": [
    {
      "commandId": "cmd_a1b2c3",
      "time": "2026-09-10T13:00:00+09:00",
      "actuator": "circFan",
      "action": "on",
      "reason": "TEMP_HIGH",
      "triggerValue": 30.4,
      "result": "ACKED",
      "message": "30.4℃ 기준 초과, 순환팬 가동"
    }
  ]
}
```

| 필드명 | 타입 | 설명 |
|---|---|---|
| reason | String | 원인 코드 (API-003과 같음) |
| triggerValue | Number | 판단 당시 값 |
| result | String | `PENDING` / `DELIVERED` / `ACKED` / `REJECTED` / `EXPIRED` |
| message | String | 화면에 바로 쓸 수 있는 문장 (백엔드가 만든다) |

### API-007-B 최근 작물 사진

[API ID] API-007-B
[API 이름] 최근 작물 사진 조회
[담당] 김승윤
[방향] 백엔드 → 모바일
[Method] GET
[URL] `/api/v1/farms/{farmId}/images/latest` (메타데이터), `/api/v1/images/{imageId}/file` (이미지 파일)
[설명] "내 작물 확인" 화면 상단의 최근 사진을 보여 준다.
[인증] 없음

[Response] 200 (`/images/latest`)
```json
{
  "imageId": 34,
  "capturedAt": "2026-10-01T14:28:00+09:00",
  "species": "Tomato",
  "imageUrl": "/api/v1/images/34/file"
}
```

[비고]
- `/images/{imageId}/file`은 JSON이 아니라 이미지(`image/jpeg`, `image/png`)를 그대로 돌려준다.
- 평가용 정답(`groundTruth*`)은 앱에 내려보내지 않는다.
- "다시 촬영" 기능은 구현 여부가 아직 정해지지 않아 넣지 않았다. 구현한다면 백엔드가 시뮬레이터에 촬영 명령을 넣는 방식(API-003의 `comm` 명령)이 된다.

---

## API-008 병해충 진단 이력 조회

> 구현 상태: 미구현.
> 확인 필요: **김우주 님, 수현 님** — 노션 API-008(김우주 님 작성)은 모바일이 사진을 직접 올려 진단을 요청하는 `POST /api/diagnoses`와 이력 조회를 담고 있다. 기능 명세상 사진은 시뮬레이터가 보내므로(API-004), 여기서는 **이력 조회만** 둔다. 모바일이 직접 진단을 요청하는 기능을 MVP에 넣을지는 따로 정한다.

[API ID] API-008
[API 이름] 병해충 진단 이력 조회
[담당] 김승윤
[방향] 백엔드 → 모바일
[Method] GET
[URL] `/api/v1/farms/{farmId}/diagnoses?size=20`
[설명] "내 작물 확인" 화면의 병해충 발생 이력과, 그에 따라 자동으로 실행된 조치를 조회한다.
[인증] 없음

[Response] 200
```json
{
  "diagnoses": [
    {
      "diagnosisId": 12,
      "diagnosedAt": "2026-09-07T09:10:00+09:00",
      "infected": true,
      "diseaseCode": "tomato-A",
      "diseaseName": "잎곰팡이병",
      "severityLevel": "중기",
      "confidence": 0.89,
      "imageUrl": "/api/v1/images/34/file",
      "responses": [
        { "commandId": "cmd_d4e5f6", "actuator": "circFan", "action": "on", "durationMinutes": 60, "result": "ACKED" }
      ],
      "guide": "환기를 철저히 하고 병든 잎을 제거하세요"
    }
  ]
}
```

| 필드명 | 타입 | 설명 |
|---|---|---|
| severityLevel | String | 중증도 단계 `초기` / `중기` / `말기`. AI가 단계로 판정한다. 판정이 없으면 `null` |
| responses | Array | 이 진단 때문에 자동으로 실행된 제어 명령. 없으면 빈 배열 |
| guide | String | 권장 조치 문구 |

---

## API-005 병해충 진단 요청

> 구현 상태: **백엔드의 호출 코드는 구현됨** (`ai/AiDiagnosisClient`, 김우주). 사진을 받았을 때 자동으로 호출하는 흐름(API-004 연동)은 미구현.
> 이 API는 **AI 서버(smartfarm-ai, FastAPI)가 제공**하고 백엔드가 호출한다. 요청·응답 형식은 AI 서버 구현을 따른다.

[API ID] API-005
[API 이름] 병해충 진단 요청
[담당] 김우주 (AI 서버), 김승윤 (백엔드 연동)
[방향] 백엔드 → AI 서버
[Method] POST
[URL] `{AI_SERVICE_URL}/diagnose`
[설명] 백엔드가 사진을 AI 서버에 보내고, **응답으로 진단 결과와 중증도를 바로 받는다(동기).**
[인증] **JWT** (`Authorization: Bearer`). 방식은 `docs/design/jwt-auth.md` 3장. 전환 전까지는 `X-API-Key` 헤더(`AI_SERVICE_API_KEY`)를 쓴다.

[Request] `multipart/form-data`
| 필드명 | 타입 | 필수 | 설명 |
|---|---|---|---|
| image | File | O | 작물 사진 (jpg/png, 백엔드 업로드 한도 20MB) |
| crop | String | O | 작물. API-004의 `species`를 소문자로 바꾼 값: `tomato`, `pepper`, `cucumber`, `strawberry`, `lettuce` |
| threshold | Number | X | 탐지 기준값. 기본 0.15 |
| tiles | Number | X | 사진 분할 수. 기본 1 |

- **시뮬레이터가 보낸 평가용 정답(`groundTruth*`)과 `estimate`는 절대 보내지 않는다** (API-004 명세). 사진과 작물 이름만 보낸다.

[Response] 200
```json
{
  "result": "detected",
  "crop": "tomato",
  "detections": [
    {
      "class": "tomato_disease18",
      "confidence": 0.93,
      "bbox": [120.0, 80.0, 180.0, 125.0],
      "severity": { "level": "중기", "risk_code": 2, "confidence": 0.71, "low_confidence": false },
      "diagnosis": { "name_kr": "잎곰팡이병", "prevention_principles": ["환기"] }
    }
  ]
}
```

| 필드명 | 타입 | 설명 |
|---|---|---|
| result | String | `detected` / `no_detection`. 병반이 없어도 200이다 |
| detections[].class | String | AI 모델 클래스. 백엔드가 팀 병해 코드로 바꾼다 (`tomato_disease18` → `tomato-A`, `ai/DiseaseCatalog`) |
| detections[].confidence | Number | 신뢰도 0~1 |
| detections[].bbox | Array | 병변 위치 `[x0, y0, x1, y1]` (원본 픽셀) |
| detections[].severity | Object | 중증도(API-006). `level`은 초기/중기/말기, `low_confidence`가 true면 참고용 |
| detections[].diagnosis | Object | AI 지식베이스의 병 정보(원인, 증상, 예방 원칙). 정상 클래스에는 없다 |

[백엔드의 결과 정리] (`ai/DiagnosisResult`)
- 사진 1장당 진단 1건으로 저장한다. 병이 여러 개 탐지되면 **신뢰도가 가장 높은 병 하나**를 고른다.
- 저장하는 값: 발생 여부, 병해 코드(`tomato-A`), 병명, 신뢰도, 중증도 단계와 risk_code, 병변 위치.

[HTTP Status]
- 200: 진단 성공 (병반이 없어도 200)
- 401: 인증 실패
- 그 외: 추론 실패

[비고]
- 타임아웃: 연결 5초, 응답 30초 (CPU 추론이 몇 초 걸릴 수 있다).
- 연결 확인: `GET /api/v1/ai/status` → `UP`(200) / `UNAUTHORIZED`, `UNREACHABLE`, `ERROR`(503).
- 백엔드는 진단 결과를 `diagnosis`에 저장하고, 병해별 대응 규칙에 따라 제어 명령(API-003)과 앱 알림(API-010)을 만든다. AI가 직접 장치를 제어하지 않는다(기능 명세).
- 결정 필요: 9/29 회의록의 아키텍처 그림에는 AI 서버 안에 "AI 조치사항 DB"가 있다. 병해별 권장 조치를 AI 응답(`diagnosis.prevention_principles`)에서 가져올지, 백엔드의 `disease_response` 테이블에 둘지 정해야 한다. **자동 제어 규칙(팬을 몇 분 켤지)은 백엔드에 둔다.**

---

## API-006 중증도 판정

[API ID] API-006
[API 이름] 중증도 판정
[방향] AI 서버 → 백엔드

- **별도로 호출하지 않는다.** 중증도는 API-005 응답의 `detections[].severity`에 함께 온다.
- 단계는 초기 / 중기 / 말기이고, `low_confidence`가 true이면 참고용으로만 보여 준다.

---

## API-010 푸시 알림 토큰 등록 (신규)

> 구현 상태: 미구현.
> 확인 필요: **수현 님** — 알림 방식이 FCM으로 정해졌으므로(9/23) 앱의 기기 토큰을 등록하는 API가 필요하다. 노션 목록에 새로 추가해야 한다.

[API ID] API-010
[API 이름] 푸시 알림 토큰 등록
[담당] 김승윤
[방향] 모바일 → 백엔드
[Method] POST
[URL] `/api/v1/fcm-tokens`
[설명] 앱을 실행하거나 FCM 토큰이 바뀔 때 호출한다. 로그인이 없으므로 등록된 모든 기기에 알림을 보낸다.
[인증] 없음

[Request Body]
```json
{ "token": "fcm-registration-token" }
```

[Response] 200 `{"ok": true}` (이미 등록된 토큰이어도 200)

[비고] **백엔드가 보내는 알림 형식 (안)** — FCM `data` 메시지
```json
{
  "type": "PEST_DETECTED",
  "title": "AI 병해충 감지 경보",
  "body": "'잎곰팡이병'이(가) 발생했습니다. 순환팬을 60분 가동합니다.",
  "diagnosisId": "12",
  "diseaseName": "잎곰팡이병",
  "action": "FAN_ON",
  "durationMinutes": "60"
}
```
- UI 시안의 병해충 경보 팝업에 들어가는 값(병명, 권장 조치, 가동 시간)을 담았다.
- FCM `data` 메시지의 값은 모두 문자열이어야 해서 숫자도 문자열로 보낸다.
- 백엔드에서 FCM을 보내려면 Firebase 프로젝트의 서비스 계정 키가 필요하다. 앱 쪽 Firebase 프로젝트를 누가 만드는지 정해야 한다.

---

## 전달·확인 요청 정리
| 대상 | 내용 |
|---|---|
| 노현석 님 | **API-009**: 시뮬레이터 로그인을 `POST /api/v1/auth/token {gatewayId, secret}` 형식으로 변경 (갱신 API 없음, 만료 시 재발급). **API-003**: 구현 완료, 로그인만 바꾸면 그대로 연결됨. 사진 전송 주기 |
| 김우주 님 | **AI 구간 JWT 전환** (`jwt-auth.md` 3장: 공유 키, 60초 토큰, 전환 순서). 진단 이력 저장 위치(백엔드 `diagnosis` 테이블), 모바일 직접 진단 요청의 MVP 포함 여부, 조치사항을 어디서 가져올지 |
| 수현 님 | API-002·007·008 응답에 필요한 값, 그래프 주기(9/29 회의: 1시간 또는 30분), "다시 촬영" 구현 여부, API-010과 알림 형식, Firebase 프로젝트 |
| 장세민 님 | 노션 API 목록 정리: API-007 복구, API-009를 JWT 내용으로 교체, API-010 추가. 수집·저장 방안 확정 |
