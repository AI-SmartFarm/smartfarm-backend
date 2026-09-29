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
- 사용: `AiDiagnosisClient.diagnose(이미지 바이트, 파일명, crop)` — `crop`은 API-004 `species`를 소문자로 바꾼 값 (`tomato`, `pepper`, ...)
- 로컬에서 AI 서버 띄우기: smartfarm-ai 저장소에서 `pip install -r requirements.txt` 후
  `uvicorn api:app --app-dir scripts --port 8000`
