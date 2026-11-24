#!/usr/bin/env bash
# First deploy to AWS (spec: M5; infra/README.md). Run from the repo root in Codespaces:
#   make aws-deploy
# Needs AWS credentials for the target account (Codespaces secrets AWS_ACCESS_KEY_ID,
# AWS_SECRET_ACCESS_KEY, AWS_REGION=us-east-1, or `aws configure sso`), plus Docker,
# Terraform, the AWS CLI, Maven and Node (all in the devcontainer).
# Safe to re-run: every step skips what already exists. Terraform shows each plan and
# asks before changing anything.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TF="$ROOT/infra/terraform"
export AWS_REGION="${AWS_REGION:-us-east-1}"
export AWS_DEFAULT_REGION="$AWS_REGION"
TAG="${IMAGE_TAG:-$(git -C "$ROOT" rev-parse --short HEAD)}"

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

for tool in terraform aws docker mvn npm; do
  command -v "$tool" >/dev/null || { echo "Missing $tool. Rebuild the codespace (Command Palette > Codespaces: Rebuild Container)."; exit 1; }
done

step "AWS account"
ACCOUNT="$(aws sts get-caller-identity --query Account --output text)" \
  || { echo "No AWS credentials. Add Codespaces secrets AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_REGION, then restart the codespace."; exit 1; }
echo "Deploying to account $ACCOUNT in $AWS_REGION"

cd "$TF"
if [ ! -f terraform.tfvars ]; then
  step "Creating terraform.tfvars"
  {
    echo "cognito_domain_prefix = \"ecommerceapp-${ACCOUNT}\""
    if [ -n "${ALARM_EMAIL:-}" ]; then echo "alarm_email = \"${ALARM_EMAIL}\""; fi
  } > terraform.tfvars
  cat terraform.tfvars
fi

step "terraform init"
terraform init -input=false

step "Image repository (ECR)"
terraform apply -target=aws_ecr_repository.backend -target=aws_ecr_lifecycle_policy.backend
ECR="$(terraform output -raw ecr_repository_url)"

step "Backend image $ECR:$TAG"
if aws ecr describe-images --repository-name "${ECR#*/}" --image-ids imageTag="$TAG" >/dev/null 2>&1; then
  echo "Already pushed"
else
  aws ecr get-login-password | docker login --username AWS --password-stdin "${ECR%%/*}"
  (cd "$ROOT/backend" && mvn -B -q spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName="$ECR:$TAG")
  docker push "$ECR:$TAG"
fi

step "Everything else (VPC, database, Redis, Cognito, ECS, CloudFront, alarms). The first run takes about 20-30 minutes"
terraform apply -var "backend_image_tag=$TAG"

step "Web app (S3 + CloudFront)"
BUCKET="$(terraform output -raw web_bucket)"
DIST="$(terraform output -raw cloudfront_distribution_id)"
(cd "$ROOT/frontend" && npm ci --no-audit --no-fund --silent && npm run -s build)
aws s3 sync "$ROOT/frontend/dist/assets" "s3://$BUCKET/assets" --cache-control "public, max-age=31536000, immutable" --only-show-errors
aws s3 sync "$ROOT/frontend/dist" "s3://$BUCKET" --delete --exclude "assets/*" --cache-control "no-cache" --only-show-errors
aws cloudfront create-invalidation --distribution-id "$DIST" --paths "/index.html" "/" --query Invalidation.Id --output text >/dev/null

step "Waiting for the backend to become healthy"
aws ecs wait services-stable --cluster "$(terraform output -raw ecs_cluster)" --services "$(terraform output -raw ecs_service)" \
  && echo "Backend is up" || echo "Not stable yet; check the ECS service events and the log group /ecs/ecommerceapp-prod/backend"

URL="$(terraform output -raw app_url)"
cat <<MSG

Done. Open: $URL
(CloudFront can take a few more minutes to answer everywhere.)

Next:
  1. Sign up in the app, then make yourself a catalog admin and sign in again:
     aws cognito-idp admin-add-user-to-group --user-pool-id $(terraform output -raw cognito_user_pool_id) --username <your email> --group-name CATALOG_ADMIN
  2. For later deploys from GitHub Actions (Actions > Deploy), create the GitHub environment
     "production" and set these variables on it:
       AWS_DEPLOY_ROLE_ARN=$(terraform output -raw deploy_role_arn)
       ECR_REPOSITORY_URL=$ECR
       ECS_CLUSTER=$(terraform output -raw ecs_cluster)
       ECS_SERVICE=$(terraform output -raw ecs_service)
       ECS_TASK_FAMILY=$(terraform output -raw ecs_task_family)
       WEB_BUCKET=$BUCKET
       CLOUDFRONT_DISTRIBUTION_ID=$DIST
  Dashboard: $(terraform output -raw dashboard_url)
MSG
