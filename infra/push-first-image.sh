#!/usr/bin/env bash
# Pushes the very first image (tag "bootstrap") so the ECS service has something to start.
# After this, GitHub Actions deploys every merge to main. Needs: AWS CLI logged in, Docker, and `terraform apply` done.
set -euo pipefail
cd "$(dirname "$0")"

REPO_URL="$(terraform output -raw ecr_repository_url)"
REGION="$(terraform output -json github_variables | python3 -c 'import sys, json; print(json.load(sys.stdin)["AWS_REGION"])')"

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${REPO_URL%%/*}"
docker build -t "$REPO_URL:bootstrap" ..
docker push "$REPO_URL:bootstrap"
echo "Pushed $REPO_URL:bootstrap. Now run: terraform apply -var desired_count=1"
