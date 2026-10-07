# 백엔드 변경 기록

`main`에 들어간 변경과, 서버·설정에 한 작업을 기록한다. 최신이 위에 온다.
- 설계 기준 문서: `docs/api/api-spec-draft.md`(API 명세), `docs/design/erd-spec.md`(ERD), `docs/design/jwt-auth.md`(JWT)
- `main`에 merge되면 GitHub Actions가 테스트 후 테스트 서버에 자동 배포한다.

---

## 2026-10-07 — 병해충 안내 문구(guide)를 AI 응답에서 가져오기 (김승윤)
- 브랜치 `feat/ai-guide` (PR 번호는 merge 후 기입)
- 배경: 제안서 아키텍처는 병해충 조치사항을 AI 서버("AI 조치사항 DB")가 맡는다. 그에 맞춰 안내 문구는 AI가, 장치 대응(순환팬 60분 등)은 백엔드가 정하도록 나눴다(기능 명세 F-04).
- API-008 `guide`: AI 응답 `detections[].diagnosis.prevention_principles`(예방·방제 원칙 목록)를 줄바꿈으로 이은 문자열. 형식은 그대로 문자열이라 앱 수정은 필요 없다.
  - AI 근거 자료가 없어 목록이 비어 오는 병(예: 고추점무늬병)은 `disease_response`의 문구로 대신하고, 그것도 없으면 `null`
- `diagnosis` 테이블에 `guide`(TEXT) 컬럼 추가. `ddl-auto=update`라 배포 시 자동으로 생기고, 기존 진단은 `null`이라 규칙 문구로 대신 나간다.
- 바꾼 코드: `ai/DiagnosisResult`(guide 추가), `diagnosis/Diagnosis`(컬럼), `diagnosis/DiagnosisController`(AI 문구 우선), `control/DiseaseResponse`(주석)
- 검증: 테스트 71개 통과(실제 AI가 필요한 2개는 건너뜀). 새 테스트 1개(AI 문구 사용, 빈 문장 제외).

## 2026-10-07 — 사진 수신·AI 진단(김우주) 통합, 사진 저장 정책, 병해 자동 대응
- PR #7 (`feat/diagnosis-response`). 김우주 PR #4·#5는 여기에 포함돼 닫음
- 담당: 김우주(사진 수신·진단, PR #5에서 가져옴), 김승윤(통합·저장 정책·자동 대응)

**포함한 작업**
- 김우주 PR #5 `codex/diagnosis-queue-memory`의 커밋 4개를 그대로 포함한다(작성자 유지). PR #4는 #5에 포함돼 있어 #4·#5는 닫는다.
  - API-004 사진 수신 `POST /api/v1/farms/{farmId}/images` → 파일은 디스크(서버 볼륨 `image-data`), 메타데이터는 `crop_image`
  - API-005 AI 진단을 백그라운드 대기열에서 한 장씩 호출 → `diagnosis` 저장
  - API-008 진단 이력 조회 `GET /api/v1/farms/{farmId}/diagnoses`
- PR #6(자동제어)과 `application.properties` 끝부분이 충돌해 양쪽 설정을 모두 남겼다.

**추가한 것 (김승윤)**
- 사진 저장 정책 (ERD 9-2)
  - 감염 순간(`pest`)·요청(`request`) 사진은 항상 저장·진단한다.
  - 주기(`routine`) 사진은 카메라별로 10분에 1장만 저장·진단한다(`IMAGE_ROUTINE_INTERVAL_SECONDS`). 저장하지 않은 사진에도 `{"ok": true, "file": null}`로 응답한다.
  - 정상으로 진단된 사진은 카메라별 최신 1장만 파일을 남긴다. 감염 사진·진단 실패 사진은 지우지 않는다. DB 기록은 남는다.
- 병해 대응 규칙 `disease_response` (ERD 4-8): `tomato-A`(잎곰팡이병) 순환팬 60분, `tomato-B`(황화잎말이바이러스) 알림만. 규칙이 없는 병은 대응하지 않는다.
- 병해 자동 대응 (기능 명세 F-04)
  - 감염 진단 → 순환팬 켜기 명령(`source=AI`, `reason=PEST_RESPONSE`, `diagnosis_id` 연결, `duration_sec=3600`)
  - 유지 시간 동안은 온도가 낮아도 자동제어가 팬을 끄지 않는다. 유지 중 재감염은 명령을 새로 만들지 않는다.
  - 유지 시간이 끝나면 온도가 켜는 기준보다 낮을 때 끈다(`reason=PEST_RESPONSE_END`).
- API-008 응답 보강: `responses`(그 진단으로 실행된 명령), `guide`(대응 규칙의 권장 조치), 파일이 지워진 사진은 `imageUrl=null`

**다른 사람 코드에서 바꾼 부분** (김우주 작성 코드)
| 파일 | 변경 |
|---|---|
| `diagnosis/DiagnosisService` | 진단 저장 뒤 감염이면 자동 대응, 정상이면 이전 정상 사진 파일 정리. 생성자에 `PestResponseService` 추가. `save()`가 저장한 엔티티를 돌려주게 변경 |
| `diagnosis/DiagnosisController` | `responses`·`guide`·`imageUrl`을 채우도록 변경 |
| `image/ImageController` | 농장 ID 확인 뒤 주기 사진 저장 여부 판단, 저장하지 않으면 `file=null` 응답 |
| `image/ImageService`, `CropImageRepository`, `CropImage`, `ImageProperties` | 저장 여부 판단, 카메라별 이전 사진 조회, 파일 삭제, `routineIntervalSeconds` 설정 |
| `test/diagnosis/DiagnosisFlowApiTest` | 잎곰팡이병 진단의 `responses`가 빈 배열이라는 기대를 "순환팬 60분 명령 1건"으로 변경. 테스트마다 이 농장의 명령을 지우는 준비 단계 추가 |
| `test/diagnosis/DiagnosisQueueTest` | 늘어난 생성자 매개변수에 가짜 `PestResponseService` 전달 |

**설정 (서버 추가 작업 없음, 기본값 사용)**
| 환경 변수 | 기본값 | 뜻 |
|---|---|---|
| `IMAGE_STORAGE_DIR` | `data/images` (서버는 볼륨 `/app/data/images`) | 사진 저장 위치 |
| `DIAGNOSIS_QUEUE_CAPACITY` | 50 | 진단 대기열 크기. 차면 그 사진은 진단하지 않는다 |
| `IMAGE_ROUTINE_INTERVAL_SECONDS` | 600 | 주기 사진 저장·진단 간격(카메라별) |

**검증**: 테스트 70개 통과(실제 AI가 필요한 2개는 건너뜀). 새 테스트 9개(`DiagnosisResponseFlowTest`).

**남은 것**: FCM 알림(Firebase 프로젝트 미정), 나머지 4개 작물의 기준값과 병해 대응 규칙, 안내 문구를 AI 응답에서 가져올지 여부.

---

## 2026-10-06 — PR #6 환경 자동제어와 제어 명령 API (김승윤)
- 작물 기준값 `crop_profile`. 서버 시작 시 토마토 임시값 등록(팬 27/25℃, 관수 30/50%)
- 텔레메트리를 받을 때마다 히스테리시스로 순환팬·관수를 판단해 `control_command`에 명령을 넣는다(백엔드가 임계값 판단 — 업무 분담·기능 명세 F-02).
- API-003 `GET /api/v1/farms/{farmId}/commands`(ACK 전까지 매번 다시 내려줌), `POST /api/v1/farms/{farmId}/commands/ack`
- 중복 방지(결과 대기 중 새 명령 없음), 쿨다운 30초, 만료 60초
- 배포 후 서버에서 고온 → 팬 명령 → ACK까지 확인

## 2026-10-02 — PR #3 JWT 기기 인증과 텔레메트리 수신 (김승윤)
- API-009 `POST /api/v1/auth/token` (`{gatewayId, secret}` → 1시간 JWT, 재발급 토큰 없음)
- 기기용 경로에만 JWT 검사(401/403). 모바일 경로는 인증 없음(사용자 로그인 없음)
- API-001 텔레메트리 수신: 최신값은 메모리, DB `telemetry`에는 1분에 1건
- `farm`, `gateway` 테이블, 서버 시작 시 `greenhouse-01`/`SIM001` 등록
- **서버 작업**: `/opt/smartfarm/.env`에 `JWT_SECRET`, `SEED_GATEWAY_SECRET` 추가(서버 안에서 무작위 생성)

## 2026-09-30 — AI 진단 서버 연동 (김우주, `main`에 직접 merge)
- `ai` 패키지: AI 서버 `POST /diagnose` 호출 클라이언트, `X-API-Key` 헤더, `GET /api/v1/ai/status`
- **서버 작업**: `/opt/smartfarm/.env`에 `AI_SERVICE_URL`, `AI_SERVICE_API_KEY` 추가 후 앱 컨테이너 재생성(김승윤)

## 2026-09-30 — PR #2 프로젝트 초기 설정과 자동 배포 (김승윤)
- Java 21, Spring Boot 4.1.1, MySQL. 로컬 실행 방법은 README
- GitHub Actions: PR마다 테스트, `main` merge 시 ECR 이미지 빌드 → SSM으로 EC2 재배포
- 서버: EC2 t3.small(서울), 인바운드 포트 없음, Cloudflare 임시 터널로 HTTPS 노출
- **AWS 작업**: OIDC 공급자, ECR `smartfarm-backend`, IAM 역할 2개, 보안 그룹, EC2 생성. 배포 역할의 신뢰 조건은 팀 저장소의 immutable subject(`repo:AI-SmartFarm@329948355/smartfarm-backend@1372890161:ref:refs/heads/main`) 형식
