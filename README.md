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

병해충 진단은 [smartfarm-ai](https://github.com/AI-SmartFarm/smartfarm-ai)의 FastAPI 서비스(`POST /diagnose`)를 동기 호출한다.
응답 한 번에 진단 결과(API-007)와 중증도(API-006)가 함께 온다. 코드는 `com.smartfarm.backend.ai` 패키지에 있다.

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

아직 없는 것: 진단 결과에 따른 자동 대응(제어 명령·알림, `disease_response` 규칙 미정), 사진 보관 정책(ERD 9-2). 서버에서는 사진이 `image-data` 볼륨에 계속 쌓이므로 시뮬레이터 전송 주기가 정해지면 정리 방식을 정해야 한다.
