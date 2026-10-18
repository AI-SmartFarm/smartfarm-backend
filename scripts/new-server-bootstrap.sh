#!/bin/bash
# 새 EC2(Ubuntu 24.04)에서 처음 한 번 돌리는 준비 스크립트. user-data로 넣는다.
#
# 하는 일: Docker, AWS CLI(ECR 로그인용), swap 2GB, /opt/smartfarm 디렉터리.
# 외부 노출은 compose.prod.yaml의 Cloudflare 터널이 맡으므로 nginx·certbot은 설치하지 않고 인바운드 포트도 열지 않는다.
# compose.prod.yaml과 deploy.sh는 GitHub Actions가 배포할 때마다 SSM으로 덮어쓰므로 저장소를 clone하지 않는다.
# 비밀값(.env)과 DB 복원은 사람이 따로 한다. user-data는 콘솔 로그에 남아서 비밀값을 여기 넣으면 안 된다.
set -euxo pipefail

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl unzip

# Docker (공식 저장소). Ubuntu 기본 저장소의 docker.io에는 compose v2 플러그인이 없다.
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
usermod -aG docker ubuntu

# AWS CLI v2. deploy.sh가 ECR 로그인에 쓴다. Ubuntu 24.04 apt에는 v2 패키지가 없다.
if ! command -v aws >/dev/null; then
  curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip
  unzip -q /tmp/awscliv2.zip -d /tmp
  /tmp/aws/install
  rm -rf /tmp/aws /tmp/awscliv2.zip
fi

# swap 2GB. t3.small(2GB)에 MySQL과 JVM을 같이 올리면 메모리가 빠듯하다.
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

install -d -m 0750 /opt/smartfarm
touch /opt/smartfarm/bootstrap-done
