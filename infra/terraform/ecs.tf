# Backend on ECS Fargate (D12) behind an ALB that only CloudFront can reach.

resource "aws_ecr_repository" "backend" {
  name                 = "${var.project}-backend"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name
  policy     = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 30 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 30 }
      action       = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${local.name}/backend"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_cluster" "main" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# --- IAM: the execution role starts the task; the task role is what the app can call ---

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-app-secrets"
  role   = aws_iam_role.execution.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = [
        aws_db_instance.main.master_user_secret[0].secret_arn,
        aws_secretsmanager_secret.redis_auth.arn,
        aws_secretsmanager_secret.oidc_client.arn,
      ]
    }]
  })
}

resource "aws_iam_role" "task" {
  name               = "${local.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

# D19: label reading calls Claude on Bedrock through the US cross-region inference profile.
resource "aws_iam_role_policy" "task_bedrock" {
  count  = var.ai_provider == "bedrock" ? 1 : 0
  name   = "bedrock-label-reading"
  role   = aws_iam_role.task.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["bedrock:InvokeModel", "bedrock:Converse"]
      Resource = [
        "arn:aws:bedrock:${var.region}:${data.aws_caller_identity.current.account_id}:inference-profile/${var.ai_model_id}",
        "arn:aws:bedrock:*::foundation-model/${trimprefix(var.ai_model_id, "us.")}*",
      ]
    }]
  })
}

# --- Task definition ---

locals {
  backend_environment = {
    JAVA_TOOL_OPTIONS             = "-XX:MaxRAMPercentage=75"
    SPRING_DATASOURCE_URL         = "jdbc:postgresql://${aws_db_instance.main.address}:5432/ecommerce?sslmode=require"
    SPRING_DATASOURCE_USERNAME    = "ecommerce"
    SPRING_DATA_REDIS_HOST        = aws_elasticache_replication_group.sessions.primary_endpoint_address
    SPRING_DATA_REDIS_PORT        = "6379"
    SPRING_DATA_REDIS_SSL_ENABLED = "true"
    APP_PUBLIC_BASE_URL           = local.public_url
    SESSION_COOKIE_SECURE         = "true"
    OIDC_CLIENT_ID                = aws_cognito_user_pool_client.bff.id
    OIDC_AUTHORIZATION_URI        = "${local.cognito_base}/oauth2/authorize"
    OIDC_TOKEN_URI                = "${local.cognito_base}/oauth2/token"
    OIDC_JWK_SET_URI              = "${local.cognito_issuer}/.well-known/jwks.json"
    OIDC_END_SESSION_URI          = "${local.cognito_base}/logout"
    OIDC_ROLES_CLAIM              = "cognito:groups"
    OIDC_USER_NAME_ATTRIBUTE      = "email"
    PAYMENTS_PROVIDER             = "test" # D20
    AI_PROVIDER                   = var.ai_provider
    AI_MODEL_ID                   = var.ai_model_id
    AWS_REGION                    = var.region
  }
  backend_secrets = {
    SPRING_DATASOURCE_PASSWORD = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::"
    SPRING_DATA_REDIS_PASSWORD = aws_secretsmanager_secret.redis_auth.arn
    OIDC_CLIENT_SECRET         = aws_secretsmanager_secret.oidc_client.arn
  }
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name             = "backend"
    image            = "${aws_ecr_repository.backend.repository_url}:${var.backend_image_tag}"
    essential        = true
    portMappings     = [{ containerPort = 8080, protocol = "tcp" }]
    environment      = [for k, v in local.backend_environment : { name = k, value = v }]
    secrets          = [for k, v in local.backend_secrets : { name = k, valueFrom = v }]
    logConfiguration = {
      logDriver = "awslogs"
      options   = {
        awslogs-group         = aws_cloudwatch_log_group.backend.name
        awslogs-region        = var.region
        awslogs-stream-prefix = "backend"
      }
    }
  }])
}

# --- Load balancer: reachable only from CloudFront, which adds a secret header ---

resource "random_password" "origin_verify" {
  length  = 40
  special = false
}

resource "aws_lb" "main" {
  name                       = local.name
  load_balancer_type         = "application"
  internal                   = false
  security_groups            = [aws_security_group.alb.id]
  subnets                    = aws_subnet.public[*].id
  idle_timeout               = 90
  drop_invalid_header_fields = true
}

resource "aws_lb_target_group" "backend" {
  name                 = "${local.name}-backend"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = aws_vpc.main.id
  deregistration_delay = 30

  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Forbidden"
      status_code  = "403"
    }
  }
}

resource "aws_lb_listener_rule" "from_cloudfront" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  condition {
    http_header {
      http_header_name = "X-Origin-Verify"
      values           = [random_password.origin_verify.result]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

resource "aws_ecs_service" "backend" {
  name                              = "backend"
  cluster                           = aws_ecs_cluster.main.id
  task_definition                   = aws_ecs_task_definition.backend.arn
  desired_count                     = var.backend_desired_count
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 120
  enable_execute_command            = false

  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.backend.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # The deploy workflow registers new task definition revisions; autoscaling owns the count.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  depends_on = [aws_lb_listener_rule.from_cloudfront]
}

resource "aws_appautoscaling_target" "backend" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.backend.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.backend_desired_count
  max_capacity       = var.backend_max_count
}

resource "aws_appautoscaling_policy" "backend_cpu" {
  name               = "${local.name}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.backend.service_namespace
  resource_id        = aws_appautoscaling_target.backend.resource_id
  scalable_dimension = aws_appautoscaling_target.backend.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
