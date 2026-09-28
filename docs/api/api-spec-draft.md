# API 명세 초안 (백엔드)

- 작성: 김승윤 (백엔드) / 2026-09-28 / **초안 — 담당자 확인 전**
- 범위: 백엔드가 제공하거나 호출하는 API 중 아직 비어 있는 API-002, 003, 005, 006, 007, 008과 새로 필요한 API-009
- API-001(환경 데이터 전송), API-004(작물 이미지 전송)는 노현석 님 명세를 그대로 따르므로 여기서 다시 쓰지 않는다.
- 9/23 공지에 따라 **인터페이스 초안**까지만 쓴다. 세부 필드와 에러 코드는 구현하면서 확정한다.
- 관련 문서: `docs/design/jwt-db-draft.md`, `docs/design/erd-spec.md`

## 0. 공통 규칙

| 항목 | 내용 |
|---|---|
| Base URL | 테스트 서버 `https://<터널 주소>` (현재 임시 주소, 바뀌면 공유) |
| 데이터 형식 | JSON (`Content-Type: application/json; charset=utf-8`), 필드 이름은 camelCase |
| 시각 형식 | 백엔드가 내보내는 시각은 ISO-8601 한국 시간 (예: `2026-10-01T14:30:00+09:00`) |
| 인증 | **시뮬레이터 → 백엔드만 JWT** (`Authorization: Bearer <accessToken>`, API-008에서 발급). 모바일 → 백엔드는 인증 없음 |
| 에러 형식 | `{"error": "설명"}` (API-001·004와 같음) |
| 공통 상태 코드 | 200 성공 / 400 잘못된 요청 / 401 토큰 없음·만료 / 403 권한 없음(토큰의 농장 ≠ URL의 농장) / 404 없음 / 500 서버 오류 |

**JWT가 필요한 API의 공통 검증** (API-001, 003, 004)
1. 서명과 만료 시간 확인 → 실패하면 `401 {"error": "invalid or expired access token"}`
2. 토큰의 `farmId`와 URL의 `{farmId}` 비교 → 다르면 `403 {"error": "farm access denied"}`
3. 기기 상태가 `ACTIVE`인지 확인 → 아니면 `403 {"error": "gateway inactive"}`

---

## API-008 JWT 인증 및 검증

> 확인 필요: **노현석 님** — 시뮬레이터 `Net/JwtAuth.cs`에 로그인·갱신 코드가 이미 있으므로, 요청 URL과 필드 이름을 그 코드에 맞출지 이 초안에 맞출지 정해야 한다.

[API ID] API-008
[API 이름] 기기 토큰 발급
[담당] 김승윤 (설계), 장세민 (구현 — 확인 필요)
[방향] 시뮬레이터 → 백엔드
[Method] POST
[URL] `/api/v1/auth/token`
[설명] 등록된 기기가 ID와 비밀값으로 JWT를 발급받는다. 발급받은 토큰으로 API-001, 003, 004를 호출한다.
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
| accessToken | String | JWT. 내용: `sub`=gatewayId, `farmId`, `iat`, `exp` |
| tokenType | String | 항상 `Bearer` |
| expiresIn | Number | 유효 시간(초). 1시간 |
| farmId | String | 이 기기가 속한 농장. 이후 API URL에 쓴다 |

[HTTP Status]
- 200: 발급 성공
- 400: 필드 누락
- 401: ID 또는 비밀값이 틀림 `{"error": "invalid credentials"}`
- 403: 비활성 기기 `{"error": "gateway inactive"}`

[비고]
- 재발급 토큰(refresh token)은 두지 않는다. 토큰이 만료돼 401을 받으면 이 API를 다시 호출한다. API-001 명세의 "토큰 버리고 재로그인 후 1회 재시도"와 같은 동작이다.

---

## API-003 장치 제어 명령 전달

> 확인 필요: **노현석 님** — 시뮬레이터는 이미 3초마다 명령을 조회한다(`Net/BackendClient.cs` `CommandLoop`, `ApplyCommands`). 그 코드가 기대하는 URL과 응답 형식에 맞춰야 한다. 아래 2가지 질문도 함께 확인한다.

시뮬레이터에는 수신 서버가 없으므로, 백엔드가 명령을 대기열에 넣어 두고 시뮬레이터가 주기적으로 **가져가는(polling)** 방식이다.

[API ID] API-003
[API 이름] 대기 중인 제어 명령 조회
[담당] 김승윤
[방향] 백엔드 → 시뮬레이터 (시뮬레이터가 요청)
[Method] GET
[URL] `/api/v1/farms/{farmId}/commands`
[설명] 아직 전달되지 않은 명령을 가져간다. 한 번 내려간 명령은 `DELIVERED`로 바뀌어 다시 내려가지 않는다.
[인증] JWT

[Response] 200
```json
{
  "commands": [
    {
      "id": "cmd_a1b2c3",
      "actuator": "circFan",
      "action": "on",
      "reason": "PEST_RESPONSE",
      "createdAt": "2026-10-01T14:30:00+09:00"
    }
  ]
}
```

| 필드명 | 타입 | 설명 |
|---|---|---|
| commands | Array | 대기 명령 목록. 없으면 빈 배열 `[]` |
| id | String | 명령 ID. 시뮬레이터가 결과를 보고할 때 같은 값을 쓴다 (`commandTrace[].id`) |
| actuator | String | `circFan`(순환팬), `waterPump`(관수 펌프), `ventFan`(배기팬). API-001 `actuators` 이름과 같다 |
| action | String | `on` / `off` |
| reason | String | 명령 원인. `TEMP_HIGH`, `TEMP_NORMAL`, `SOIL_LOW`, `SOIL_ENOUGH`, `PEST_RESPONSE` (시뮬레이터 동작에는 영향 없음, 로그용) |
| createdAt | String | 명령 생성 시각 |

[HTTP Status]
- 200: 성공 (대기 명령이 없어도 200)
- 401 / 403: 공통 규칙 참고

[비고]
- **실행 결과 보고(ACK):** 시뮬레이터가 이미 API-001 텔레메트리에 `commandTrace`(최근 명령 32개의 수락 여부, 처리 직후 장치 상태)를 담아 보낸다. 백엔드는 이것으로 명령 상태를 `ACKED`/`REJECTED`로 바꾼다. **별도의 ACK API는 만들지 않는 것을 제안한다.**
- **가동 시간:** 병해 대응처럼 "60분 동안 팬 가동"이 필요하면, 시뮬레이터에 타이머를 두지 않고 **백엔드가 60분 뒤 `off` 명령을 다시 넣는다.** 시뮬레이터 수정이 필요 없다.
- 일정 시간(안: 1분) 안에 전달되지 않은 명령은 `EXPIRED` 처리하고 내려보내지 않는다. 오래된 명령이 한꺼번에 실행되는 것을 막기 위해서다.
- 노현석 님께 확인할 것:
  1. 현재 코드의 명령 조회 URL과 응답 필드 이름
  2. API-001의 `commandDelivery`에 `push`가 있는데, 시뮬레이터가 polling 말고 다른 방식도 지원하는지

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

## API-007 로그 조회

> 확인 필요: **수현 님** — 목록 기존 이름은 "병해충 제어 로그 조회"지만, 화면(온도 로그, 토양 수분 로그, 내 작물 확인)에 필요한 조회를 묶어서 3개로 제안한다.

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

### API-007-B 병해충 진단 로그

[API ID] API-007-B
[API 이름] 병해충 관리 로그 조회
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
      "diseaseName": "잎곰팡이병",
      "severity": 0.5,
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

### API-007-C 최근 작물 사진

[API ID] API-007-C
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
- "다시 촬영" 기능은 구현 여부가 아직 정해지지 않아 넣지 않았다. 구현한다면 백엔드가 시뮬레이터에 촬영 명령을 넣는 방식(API-003)이 된다.

---

## API-005 병해충 진단 요청

> 확인 필요: **김우주 님** — 이 API는 **AI 서버가 제공**하고 백엔드가 호출한다. 아래는 "백엔드가 보내고 받고 싶은 형식"의 제안이다. 경로, 필드 이름, 출력 형식은 AI 쪽 구현에 맞춰 정한다.

[API ID] API-005
[API 이름] 병해충 진단 요청
[담당] 김우주 (제공), 김승윤 (호출)
[방향] 백엔드 → AI 서버
[Method] POST
[URL] `{AI 서버 주소}/api/v1/diagnose` (AI 쪽에서 결정)
[설명] 백엔드가 시뮬레이터에게서 받은 사진을 AI 서버에 보내고, **응답으로 진단 결과를 바로 받는다(동기).**
[인증] 회의에서 결정 (AI 서버가 외부에 공개되면 `X-API-Key` 헤더 등 공유 키 제안)

[Request] `multipart/form-data`
| 필드명 | 타입 | 필수 | 설명 |
|---|---|---|---|
| image | File | O | 작물 사진 (jpg/png) |
| species | String | X | 작물 종류 (`Tomato` 등). 모델이 작물 정보를 쓴다면 전달 |

- **시뮬레이터가 보낸 평가용 정답(`groundTruthPest` 등)은 절대 보내지 않는다** (API-004 명세). 사진만 보고 판단해야 한다.

[Response] 200 (제안)
```json
{
  "infected": true,
  "diseaseCode": "tomato-A",
  "diseaseName": "잎곰팡이병",
  "confidence": 0.89,
  "severity": 0.5,
  "boxes": [ { "x": 120, "y": 80, "width": 60, "height": 45 } ],
  "modelVersion": "yolov8-v1"
}
```

| 필드명 | 타입 | 설명 |
|---|---|---|
| infected | Boolean | 병해 발생 여부 |
| diseaseCode | String | 병해 코드. **API-004 라벨(`tomato-A` 형식)과 같게** 해 주면 백엔드가 대응 규칙을 바로 찾을 수 있다. 정상이면 `null` |
| diseaseName | String | 병명 |
| confidence | Number | 신뢰도 0~1 |
| severity | Number/String | 중증도. 0~1 수치인지 초기/중기/말기인지 확인 필요 |
| boxes | Array | 병변 위치 (9/15 회의록의 바운딩 박스). 없으면 생략 가능 |
| modelVersion | String | 모델 버전 |

[HTTP Status]
- 200: 진단 성공
- 400: 이미지 형식 오류
- 500: 추론 실패

[비고]
- 백엔드는 응답을 받으면 `diagnosis`에 저장하고, 병해별 대응 규칙에 따라 제어 명령(API-003)과 앱 알림(FCM)을 만든다.
- 응답 대기 시간 제한: 안 10초. 실패하면 백엔드가 기록만 남기고 다음 사진에서 다시 시도한다.
- 김우주 님께 확인할 것: AI 서버를 어디에 띄우는지(같은 EC2인지 별도인지), 요청 형식(multipart인지 base64 JSON인지), 추론 1건에 걸리는 시간

---

## API-006 병해충 진단 결과 전달

> 확인 필요: **김우주 님**

[API ID] API-006
[API 이름] 병해충 진단 결과 전달
[방향] AI 서버 → 백엔드

[제안] **API-005를 동기 방식으로 하면 이 API는 필요 없다.** 진단 결과가 API-005의 응답으로 바로 오기 때문이다.
- 추론이 오래 걸려서(예: 수십 초) AI 서버가 나중에 백엔드로 결과를 다시 보내야 하는 경우에만 이 API를 만든다.
- 그 경우 URL 안: `POST /api/v1/diagnoses/{requestId}/result`, 인증은 공유 키. Body는 API-005 응답과 같은 형식으로 한다.
- 참고: 현재 노션 API-006 본문에는 API-001의 요청 예시가 잘못 붙어 있다.

---

## API-009 FCM 토큰 등록 (신규)

> 확인 필요: **수현 님** — 알림 방식이 FCM으로 정해졌으므로(9/23) 앱의 기기 토큰을 등록하는 API가 필요하다.

[API ID] API-009
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

## 확인 요청 정리
| 대상 | 확인할 것 |
|---|---|
| 노현석 님 | API-008 로그인 형식(`JwtAuth.cs`), API-003 명령 조회 URL·응답 형식(`BackendClient.cs`), ACK를 `commandTrace`로 대신하는 것, `commandDelivery: push`의 의미, 이미지 전송 주기 |
| 김우주 님 | API-005 경로·요청·응답 형식, 병해 코드 형식(`tomato-A`), 중증도 형식, 동기 처리 가능 여부(→ API-006 필요 여부), AI 서버 위치와 인증 |
| 수현 님 | API-002·007 응답에 필요한 값, 그래프 기간, "다시 촬영" 구현 여부, API-009와 알림 형식, Firebase 프로젝트 |
| 장세민 님 | API-007을 3개로 나누고 API-009를 추가하는 것, JWT 구현 분담 |
