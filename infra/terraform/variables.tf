variable "region" {
  description = "AWS region (D21)"
  type        = string
  default     = "us-east-1"
}

variable "project" {
  type    = string
  default = "ecommerceapp"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "backend_image_tag" {
  description = "Backend image tag in ECR for the first deploy; later deploys come from the deploy workflow"
  type        = string
  default     = "latest"
}

variable "backend_cpu" {
  type    = number
  default = 1024
}

variable "backend_memory" {
  type    = number
  default = 2048
}

variable "backend_desired_count" {
  description = "Tasks across two AZs; autoscaling may add more"
  type        = number
  default     = 2
}

variable "backend_max_count" {
  type    = number
  default = 4
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
}

variable "db_multi_az" {
  description = "RDS Multi-AZ standby (spec: M5). Roughly doubles the database cost; set false for a cheaper non-production stack"
  type        = bool
  default     = true
}

variable "db_allocated_storage_gb" {
  type    = number
  default = 20
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "ai_provider" {
  description = "\"disabled\" or \"bedrock\" (D19). Bedrock model access must be enabled in the account first"
  type        = string
  default     = "disabled"

  validation {
    condition     = contains(["disabled", "bedrock"], var.ai_provider)
    error_message = "ai_provider must be \"disabled\" or \"bedrock\"."
  }
}

variable "ai_model_id" {
  type    = string
  default = "us.anthropic.claude-sonnet-5-5"
}

variable "cognito_domain_prefix" {
  description = "Prefix for the Cognito hosted sign-in domain; must be globally unique"
  type        = string
}

variable "alarm_email" {
  description = "Email for alarm notifications (confirm the subscription email). Empty: no subscription"
  type        = string
  default     = ""
}

variable "github_repository" {
  description = "owner/repo allowed to deploy through GitHub Actions OIDC"
  type        = string
  default     = "kganesh/ecommerceapp"
}

variable "create_github_oidc_provider" {
  description = "False if the account already has the token.actions.githubusercontent.com OIDC provider"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  type    = number
  default = 30
}
