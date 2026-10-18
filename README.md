# smartfarm-backend

AI 기반 개방형 모바일 스마트팜 관리 시스템의 백엔드 (Java 21, Spring Boot, MySQL)

## 로컬 실행

1. Java 21: `mise install` (버전은 `mise.toml`에 고정)
2. 환경 변수: `.env.example`을 복사해 `.env`를 만들고 값을 채운다. `.env`는 커밋하지 않는다.
3. MySQL: `docker compose up -d`
   - 로컬에 MySQL이 이미 3306을 쓰고 있으면 `.env`의 `DB_PORT`를 3307 등으로 바꾼다.
4. 서버: `./gradlew bootRun`
5. 상태 확인: `curl localhost:8080/actuator/health` → `{"status":"UP"}`
   - 8080이 사용 중이면 `SERVER_PORT=8081 ./gradlew bootRun`으로 포트를 바꾼다.

## AI 진단 서비스 연동 (API-005)

병해충 진단은 [smartfarm-ai](https://github.com/AI-SmartFarm/smartfarm-ai)의 FastAPI 서비스(`POST /diagnose`)를 호출한다.
사진 수신 요청과 분리해 백그라운드 대기열에서 한 장씩 호출하며, 응답 한 번에 진단 결과와 중증도(API-006), 예방·방제 원칙이 함께 온다. 코드는 `com.smartfarm.backend.ai` 패키지에 있다.

- 주소: `AI_SERVICE_URL` (기본 `http://localhost:8000`), 읽기 타임아웃 30초
- 인증: AI 서버에 `API_KEY`가 설정돼 있으면 같은 값을 `AI_SERVICE_API_KEY`로 넣는다 (`X-API-Key` 헤더로 전송, 없으면 401)
- 사용: `AiDiagnosisClient.diagnose(이미지 바이트, 파일명, crop)` — `crop`은 API-004 `species`를 소문자로 바꾼 값 (`tomato`, `pepper`, ...)
- 연결 확인: `GET /api/v1/ai/status` → `{"status":"UP","latencyMs":...}` (200). 아니면 503이고 `status`로 원인을 구분한다: `UNAUTHORIZED`(키 불일치), `UNREACHABLE`(AI 서버 꺼짐·주소 틀림·터널 주소 바뀜), `ERROR`
- 로컬에서 AI 서버 띄우기: smartfarm-ai 저장소에서 `pip install -r requirements.txt` 후
  `uvicorn api:app --app-dir scripts --port 8000`

## 사진 수신과 진단 이력 (API-004 → API-005 → API-008)

1. 시뮬레이터가 `POST /api/v1/farms/{farmId}/images`(JWT)로 사진을 보내면 파일을 `IMAGE_STORAGE_DIR`(기본 `data/images`)에 쓰고 `crop_image`에 남긴 뒤 바로 200을 돌려준다.
2. 식물이 찍힌 사진이면 백그라운드에서 한 장씩 AI 서버에 진단을 요청해 `diagnosis`에 저장한다. 평가용 정답(`groundTruth*`)은 `crop_image.gt_*`에만 남고 AI에는 보내지 않는다.
   - AI 호출이 실패하면 `diagnosed_at`이 비어 있는 행이 남고 이력에는 나오지 않는다.
   - 대기열(`DIAGNOSIS_QUEUE_CAPACITY`, 기본 50)이 차면 새 사진은 진단하지 않고 서버 로그에 경고를 남긴다.
3. 앱은 `GET /api/v1/farms/{farmId}/diagnoses?size=20`으로 이력을, `GET /api/v1/images/{imageId}/file`로 사진을 받는다.

### 사진 보관 정책 (ERD 9-2)

서버 디스크를 지키려고 모든 사진을 저장하지 않는다.

- 감염 순간(`trigger=pest`)·요청(`request`) 사진은 항상 저장하고 진단한다.
- 주기(`routine`) 사진은 카메라별로 `IMAGE_ROUTINE_INTERVAL_SECONDS`(기본 600초)에 1장만 저장·진단한다. 저장하지 않은 사진에도 `{"ok": true, "file": null}`로 응답한다.
- 정상으로 진단된 사진은 카메라별 최신 1장만 파일을 남긴다(DB 기록은 유지, 이력의 `imageUrl`은 `null`). 감염 사진과 진단 실패 사진은 지우지 않는다.

### 병해 자동 대응 (기능 명세 F-04)

AI는 병을 판단하고, 장치 대응은 백엔드가 `disease_response` 규칙으로 정해 제어 명령(API-003)을 만든다.

| 병해 코드 | 자동 대응 | 안내 문구(`guide`) |
|---|---|---|
| `tomato-A` 잎곰팡이병 | 순환팬 60분 가동. 이 동안은 온도가 낮아도 팬을 끄지 않고, 끝나면 온도 기준에 따라 끈다 | AI 응답의 예방·방제 원칙 |
| `tomato-B` 황화잎말이바이러스 | 알림만 (장치 제어 없음) | AI 응답의 예방·방제 원칙 |
| 규칙이 없는 병 | 대응하지 않음 | AI 응답의 예방·방제 원칙 |

- AI 근거 자료가 없어 예방·방제 원칙이 비어 오면 `disease_response`의 문구로 대신한다.
- 진단 이력(API-008)의 `responses`에 그 진단으로 실행된 명령이, `guide`에 안내 문구가 들어간다.
- 중증도는 자동 대응의 근거로 쓰지 않는다(API-006 권고).

아직 없는 것: FCM 푸시 알림(API-010), 나머지 4개 작물의 기준값과 병해 대응 규칙.
