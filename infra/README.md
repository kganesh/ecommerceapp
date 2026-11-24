# infra

Terraform for AWS (spec: Milestones > M5 Production readiness; D12, D14, D20–D22).
Everything is in `terraform/`, one stack in **us-east-1**.

```
Browser ──HTTPS──> CloudFront ──> S3 (SPA)
                       └─ /api/*, /oauth2/*, /login/*, /logout ──HTTP + secret header──> ALB ──> ECS Fargate (backend, 2+ tasks)
                                                                                             ├─> RDS PostgreSQL 17 (Multi-AZ, no backups: D22)
                                                                                             ├─> ElastiCache Redis (sessions, TLS + auth)
                                                                                             ├─> Cognito (sign-in, cognito:groups roles)
                                                                                             └─> Bedrock (label reading, when ai_provider = "bedrock")
```

| File | What it creates |
| --- | --- |
| `network.tf` | VPC over 2 AZs, public/private subnets, one NAT gateway, S3 endpoint, security groups |
| `data.tf` | RDS PostgreSQL, ElastiCache Redis, Secrets Manager secrets |
| `cognito.tf` | User pool, SHOPPER / CATALOG_ADMIN groups, hosted sign-in, BFF client, auto-SHOPPER Lambda |
| `ecs.tf` | ECR, ECS cluster, task definition, service with autoscaling, ALB that only CloudFront can use |
| `cdn.tf` | S3 bucket for the SPA, CloudFront with SPA routing and API pass-through |
| `observability.tf` | Log-based safety metrics, alarms to SNS/email, CloudWatch dashboard |
| `github.tf` | OIDC role for the manual **Deploy** workflow |

## First deploy

Needs Terraform 1.9+, the AWS CLI signed in to the target account, and Docker.

1. `cd infra/terraform && cp terraform.tfvars.example terraform.tfvars`, then set `cognito_domain_prefix` (globally unique) and `alarm_email`.
2. Create the image repository first, push a first image, then create the rest:
   ```
   terraform init
   terraform apply -target=aws_ecr_repository.backend
   aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin $(terraform output -raw ecr_repository_url | cut -d/ -f1)
   (cd ../../backend && mvn -B spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=$(cd ../infra/terraform && terraform output -raw ecr_repository_url):latest)
   docker push $(terraform output -raw ecr_repository_url):latest
   terraform apply
   ```
3. In GitHub, create the environment **production** (add yourself as required reviewer) and set the variables listed at the top of `.github/workflows/deploy.yml` from `terraform output`. Then run **Actions > Deploy**; it builds, pushes, rolls out the backend and publishes the SPA.
4. Open `terraform output -raw app_url`, sign up, then make yourself an admin:
   `aws cognito-idp admin-add-user-to-group --user-pool-id $(terraform output -raw cognito_user_pool_id) --username <your email> --group-name CATALOG_ADMIN` (sign out and in again).
5. Confirm the alarm subscription email.

Seed data in `local/seed` is never loaded on AWS; add products through the admin screens.

## Cost notes

The biggest monthly items are the RDS Multi-AZ standby (`db_multi_az = false` halves the database cost), the NAT gateway, two Fargate tasks and the ALB. There are no database backups (D22): deletion protection is on and a final snapshot is taken if the database is destroyed through Terraform, but a failure or a bad change cannot be undone.

## Known limits (for the security review)

- CloudFront reaches the ALB over HTTP inside AWS, guarded by a secret header and the CloudFront-only prefix list. Add a custom domain with an ACM certificate to make that hop HTTPS.
- One NAT gateway: an AZ outage stops outbound calls (Cognito token exchange, Bedrock) until it recovers.
- The app uses CloudFront's default `*.cloudfront.net` domain until a custom domain is added.
