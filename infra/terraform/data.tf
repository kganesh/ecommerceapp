# --- PostgreSQL: the system of record ---
# D22: no automated backups or point-in-time recovery in Phase 1, to save cost. A database
# loss loses catalog, profiles, acknowledgment audit and orders. Deletion protection stays on,
# and deleting the database through Terraform still takes a final snapshot.

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_parameter_group" "postgres" {
  name   = "${local.name}-pg17"
  family = "postgres17"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

resource "aws_db_instance" "main" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class
  multi_az       = var.db_multi_az

  db_name  = "ecommerce"
  username = "ecommerce"
  # RDS creates and rotates the password in Secrets Manager; the task reads it from there.
  manage_master_user_password = true

  allocated_storage     = var.db_allocated_storage_gb
  max_allocated_storage = var.db_allocated_storage_gb * 5
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false

  backup_retention_period   = 0 # D22
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-final"

  auto_minor_version_upgrade      = true
  enabled_cloudwatch_logs_exports = ["postgresql"]
}

# --- Redis: sessions only (D16); TLS and auth (spec: Security) ---

resource "random_password" "redis_auth" {
  length  = 48
  special = false
}

resource "aws_elasticache_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_replication_group" "sessions" {
  replication_group_id = local.name
  description          = "Spring Session store for the BFF"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  num_cache_clusters   = 1
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  auth_token                 = random_password.redis_auth.result

  # Sessions are disposable: a lost node signs people out, nothing else.
  snapshot_retention_limit = 0
}

# --- Secrets the backend reads at start (never in the task definition as plain text) ---

resource "aws_secretsmanager_secret" "redis_auth" {
  name                    = "${local.name}/redis-auth"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "redis_auth" {
  secret_id     = aws_secretsmanager_secret.redis_auth.id
  secret_string = random_password.redis_auth.result
}

resource "aws_secretsmanager_secret" "oidc_client" {
  name                    = "${local.name}/oidc-client-secret"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "oidc_client" {
  secret_id     = aws_secretsmanager_secret.oidc_client.id
  secret_string = aws_cognito_user_pool_client.bff.client_secret
}
