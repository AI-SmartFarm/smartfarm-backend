#!/bin/bash
# EC2의 /opt/smartfarm에서 SSM으로 실행된다. 사용법: deploy.sh <이미지 태그>
# GitHub Actions와 수동 배포가 같은 절차를 쓰도록 이 스크립트 하나로 모은다.
set -euo pipefail

IMAGE_TAG=${1:?이미지 태그가 필요하다}
REGION=ap-northeast-2
COMPOSE="docker compose -f compose.prod.yaml"
cd /opt/smartfarm

REGISTRY=$(grep '^ECR_REGISTRY=' .env | cut -d= -f2)
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

export IMAGE_TAG
$COMPOSE pull app
$COMPOSE up -d
docker image prune -f

# quick tunnel 주소는 로그로만 알 수 있어서, 배포 결과에 주소를 남겨 팀에 공유하기 쉽게 한다.
URL=""
for _ in $(seq 1 30); do
  URL=$($COMPOSE logs tunnel 2>/dev/null | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1 || true)
  [ -n "$URL" ] && break
  sleep 2
done
if [ -z "$URL" ]; then
  echo "터널 주소를 찾지 못했다"
  exit 1
fi

# 앱이 뜨는 동안에는 터널이 502를 돌려주므로 UP이 나올 때까지 기다린다.
for _ in $(seq 1 36); do
  if curl -fs --max-time 5 "$URL/actuator/health" | grep -q '"status":"UP"'; then
    echo "배포 완료: $URL (image $IMAGE_TAG)"
    exit 0
  fi
  sleep 5
done

echo "health 확인 실패: $URL"
$COMPOSE logs --tail 50 app
exit 1
