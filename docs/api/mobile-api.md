# GrowCare 모바일 앱용 백엔드 API

모바일 앱(프론트) 담당이 보는 문서다. 백엔드 전체 명세(시뮬레이터·AI 구간 포함)는 노션 API 명세에 있다.
마지막 갱신: 2026-10-28 (김승윤)

## 0. 공통

| 항목 | 내용 |
|---|---|
| 서버 주소 | 단톡에 공지한 테스트 서버 주소 (Cloudflare 임시 터널이라 바뀌면 다시 공지) |
| Swagger | `{서버 주소}/swagger-ui.html` — 구현된 API를 브라우저에서 바로 호출해 볼 수 있다 |
| 인증 | **없음.** 앱에는 사용자 로그인이 없어서 모바일 API는 토큰 없이 호출한다 (JWT는 시뮬레이터·AI 구간에만 쓴다) |
| 농장 ID | `greenhouse-01` (지금은 농장 하나) |
| 형식 | JSON, 필드 이름은 camelCase |
| 시각 | 한국 시간 ISO-8601 (예: `2026-10-28T14:30:00+09:00`) |
| 오류 본문 | `{"error": "설명"}` |
| 상태 코드 | 200 성공 / 400 잘못된 요청 / 404 없음 / 500 서버 오류 |

### 구현 상태 한눈에 보기

| API | 내용 | 상태 |
|---|---|---|
| API-008 | 병해충 진단 이력 | ✅ 사용 가능 |
| — | 사진 파일 조회 | ✅ 사용 가능 |
| API-002-A | 현재 농장 상태 (대시보드) | ⏳ 예정 — 아래 설계안, 의견 받는 중 |
| API-002-B | 환경 데이터 추이 (그래프) | ⏳ 예정 |
| API-007-A | 제어 활동 로그 | ⏳ 예정 |
| API-007-B | 최근 작물 사진 | ⏳ 예정 |
| API-010 | 푸시 알림(FCM) 토큰 등록 | ⏳ 예정 — Firebase 프로젝트를 정한 뒤 |

### 화면별로 쓰는 API

| 화면 (UI 시안) | API |
|---|---|
| 메인 대시보드 (현재 온도·토양 수분) | API-002-A |
| 온도 로그 / 토양 수분 로그 — 그래프 | API-002-B |
| 온도 로그 / 토양 수분 로그 — 활동 로그 | API-007-A (`type=TEMP` / `SOIL`) |
| 내 작물 확인 — 상단 사진 | API-007-B + 사진 파일 조회 |
| 내 작물 확인 — 병해충 로그 | API-008 |
| 병해충 감지 알림 팝업 | API-010(FCM) 전까지는 API-008을 주기적으로 조회해 새 `infected=true` 진단을 확인 |

---

## 1. 사용 가능한 API

### API-008 병해충 진단 이력 조회

`GET /api/v1/farms/{farmId}/diagnoses?size=20`

완료된 진단을 최신순으로 준다. 감염이면 그 진단으로 자동 실행된 조치(`responses`)와 안내 문구(`guide`)가 함께 온다.

| 쿼리 | 타입 | 필수 | 설명 |
|---|---|---|---|
| size | Integer | 아니오 | 개수. 기본 20, 1~100 (범위 밖이면 400) |

```json
{
  "diagnoses": [
    {
      "diagnosisId": 12,
      "diagnosedAt": "2026-10-06T10:10:44+09:00",
      "infected": true,
      "diseaseCode": "tomato-A",
      "diseaseName": "잎곰팡이병",
      "severityLevel": "중기",
      "severityLowConfidence": false,
      "confidence": 0.93,
      "imageUrl": "/api/v1/images/42/file",
      "responses": [
        { "commandId": "cmd_aa9c4d567057", "actuator": "circFan", "action": "on", "durationMinutes": 60, "result": "ACKED" }
      ],
      "guide": "습도를 낮추고 환기를 철저히 한다.\n병든 잎은 일찍 제거한다."
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| diagnosisId | Number | 진단 ID |
| diagnosedAt | String | 진단 완료 시각 |
| infected | Boolean | 병이 탐지됐으면 `true`. `false`는 "병을 못 찾음"이지 정상 확정은 아니다 |
| diseaseCode | String / null | 팀 병해 코드 (`tomato-A` 등). 병이 없으면 `null` |
| diseaseName | String / null | 병 이름 (`잎곰팡이병`, `황화잎말이바이러스` 등) |
| severityLevel | String / null | 중증도 `초기` / `중기` / `말기`. 판정이 없으면 `null` |
| severityLowConfidence | Boolean / null | `true`면 중증도 정확도가 낮다 → 앱에서 "참고용"으로 표시 |
| confidence | Number / null | 탐지 점수 0~1 |
| imageUrl | String / null | 진단한 사진 경로. **서버 주소를 앞에 붙여** 이미지로 띄운다. 정상 사진은 카메라별 최신 1장만 파일이 남아서 `null`일 수 있다 → "이미지 없음" 표시 |
| responses | Array | 그 진단으로 백엔드가 자동 실행한 조치. 없으면 `[]` (예: 황화잎말이바이러스는 알림만이라 `[]`) |
| responses[].actuator | String | `circFan`(순환팬) |
| responses[].action | String | `on` / `off` |
| responses[].durationMinutes | Number / null | 가동 시간(분). 예: 60 |
| responses[].result | String | `PENDING`(전달 전) / `DELIVERED`(전달됨) / `ACKED`(장치 적용) / `REJECTED`(장치 거절) / `EXPIRED`(응답 없음) |
| guide | String / null | 사용자 안내 문구. 여러 문장은 `\n`으로 구분된다. 병이 없으면 `null` |

- 이력이 없으면 `{"diagnoses": []}` (200).
- UI 시안의 "순환팬 자동 가동 중, 60분 연속 가동"은 `responses[0]`의 `actuator`, `durationMinutes`로 그리면 된다.

### 사진 파일 조회

`GET /api/v1/images/{imageId}/file`

- JSON이 아니라 **이미지 자체**(`image/jpeg` 또는 `image/png`)를 돌려준다. 이미지 로더(Glide 등)에 `{서버 주소}{imageUrl}`을 그대로 넘기면 된다.
- 사진이 없으면 404.

---

## 2. 예정 API (설계안 — 필드 의견 주세요)

아래는 아직 구현 전이다. 화면에 필요한 값이 빠졌거나 형식을 바꾸고 싶으면 알려 주면 반영해서 구현한다.

### API-002-A 현재 농장 상태 (대시보드)

`GET /api/v1/farms/{farmId}/status`

```json
{
  "farmId": "greenhouse-01",
  "updatedAt": "2026-10-28T14:30:00+09:00",
  "connected": true,
  "crop": { "species": "Tomato", "nameKo": "토마토" },
  "temperature": { "value": 24.5, "targetMin": 22.0, "targetMax": 26.0, "state": "NORMAL" },
  "soilMoisture": { "value": 32.0, "targetMin": 30.0, "targetMax": 50.0, "state": "NORMAL" },
  "devices": { "circFan": false, "waterPump": false },
  "latestDiagnosis": { "diagnosisId": 12, "infected": false, "diseaseName": null, "diagnosedAt": "2026-10-28T14:00:00+09:00" }
}
```

| 필드 | 설명 |
|---|---|
| updatedAt | 시뮬레이터가 마지막으로 센서값을 보낸 시각 |
| connected | 시뮬레이터가 최근(30초 안)에 센서값을 보냈는지 |
| temperature, soilMoisture | 현재값, 적정 범위(작물 기준값), 상태 `NORMAL` / `HIGH` / `LOW`. 센서가 없으면 `value`가 `null` |
| devices | 장치가 지금 켜져 있는지 |
| latestDiagnosis | 가장 최근 진단. 없으면 `null` |

- 센서값을 한 번도 받지 않았으면 404.

### API-002-B 환경 데이터 추이 (그래프)

`GET /api/v1/farms/{farmId}/telemetry?range=24h`

| 쿼리 | 설명 |
|---|---|
| range | `1h` / `24h` / `7d` (안). 그래프가 너무 촘촘하지 않게 백엔드가 구간 평균으로 줄인다: `1h`는 1분, `24h`는 30분, `7d`는 3시간 간격 |

```json
{
  "range": "24h",
  "intervalMinutes": 30,
  "points": [
    { "time": "2026-10-28T10:00:00+09:00", "temperature": 23.8, "soilMoisture": 41.0 },
    { "time": "2026-10-28T10:30:00+09:00", "temperature": 24.6, "soilMoisture": 38.5 }
  ]
}
```

### API-007-A 제어 활동 로그

`GET /api/v1/farms/{farmId}/control-logs?type=TEMP&size=20`

| 쿼리 | 설명 |
|---|---|
| type | `TEMP`(온도 → 순환팬) / `SOIL`(토양 수분 → 관수) / `PEST`(병해 대응). 없으면 전체 |
| size | 개수, 기본 20 |
| before | 이 시각 이전 로그 (더 보기) |

```json
{
  "logs": [
    {
      "commandId": "cmd_a1b2c3",
      "time": "2026-10-28T13:00:00+09:00",
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

- `message`는 백엔드가 만들어 주는 문장이라 화면에 그대로 쓰면 된다.
- `reason`: `TEMP_HIGH`, `TEMP_NORMAL`, `SOIL_LOW`, `SOIL_ENOUGH`, `PEST_RESPONSE`, `PEST_RESPONSE_END`

### API-007-B 최근 작물 사진

`GET /api/v1/farms/{farmId}/images/latest`

```json
{ "imageId": 34, "capturedAt": "2026-10-28T14:28:00+09:00", "species": "Tomato", "imageUrl": "/api/v1/images/34/file" }
```

- 사진은 위의 사진 파일 조회로 띄운다.

### API-010 푸시 알림 토큰 등록 (FCM)

Firebase 프로젝트를 누가 만들지 정한 뒤 설계한다. 그전까지 병해충 알림은 API-008을 주기적으로 조회해서 처리한다.
