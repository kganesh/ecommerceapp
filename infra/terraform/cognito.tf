# Identity provider on AWS (D14): Cognito hosted sign-in, roles from the cognito:groups claim.
# New sign-ups join SHOPPER automatically; make a catalog admin with
#   aws cognito-idp admin-add-user-to-group --user-pool-id <id> --username <email> --group-name CATALOG_ADMIN

resource "aws_cognito_user_pool" "main" {
  name                     = local.name
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]
  deletion_protection      = "ACTIVE"

  password_policy {
    minimum_length    = 12
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = false
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  lambda_config {
    post_confirmation = aws_lambda_function.add_to_shopper.arn
  }
}

resource "aws_cognito_user_group" "shopper" {
  user_pool_id = aws_cognito_user_pool.main.id
  name         = "SHOPPER"
  description  = "Parents shopping for their children"
}

resource "aws_cognito_user_group" "catalog_admin" {
  user_pool_id = aws_cognito_user_pool.main.id
  name         = "CATALOG_ADMIN"
  description  = "Staff who maintain products and allergen data"
}

resource "aws_cognito_user_pool_domain" "main" {
  domain       = var.cognito_domain_prefix
  user_pool_id = aws_cognito_user_pool.main.id
}

resource "aws_cognito_user_pool_client" "bff" {
  name            = "${local.name}-bff"
  user_pool_id    = aws_cognito_user_pool.main.id
  generate_secret = true

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = ["${local.public_url}/login/oauth2/code/idp"]
  logout_urls                          = ["${local.public_url}/"]

  prevent_user_existence_errors = "ENABLED"
  id_token_validity             = 60
  access_token_validity         = 60
  refresh_token_validity        = 1
  token_validity_units {
    id_token      = "minutes"
    access_token  = "minutes"
    refresh_token = "days"
  }
}

locals {
  cognito_base   = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.region}.amazoncognito.com"
  cognito_issuer = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

# --- Post-confirmation trigger: every new account starts as a SHOPPER ---

data "archive_file" "add_to_shopper" {
  type        = "zip"
  output_path = "${path.module}/.build/add_to_shopper.zip"

  source {
    filename = "index.py"
    content  = <<-PY
      import boto3

      cognito = boto3.client("cognito-idp")

      def handler(event, context):
          if event.get("triggerSource") == "PostConfirmation_ConfirmSignUp":
              cognito.admin_add_user_to_group(
                  UserPoolId=event["userPoolId"],
                  Username=event["userName"],
                  GroupName="SHOPPER",
              )
          return event
    PY
  }
}

resource "aws_iam_role" "add_to_shopper" {
  name               = "${local.name}-add-to-shopper"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "add_to_shopper_logs" {
  role       = aws_iam_role.add_to_shopper.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "add_to_shopper" {
  name   = "add-to-shopper-group"
  role   = aws_iam_role.add_to_shopper.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "cognito-idp:AdminAddUserToGroup"
      Resource = "arn:aws:cognito-idp:${var.region}:${data.aws_caller_identity.current.account_id}:userpool/*"
    }]
  })
}

resource "aws_lambda_function" "add_to_shopper" {
  function_name    = "${local.name}-add-to-shopper"
  role             = aws_iam_role.add_to_shopper.arn
  runtime          = "python3.13"
  handler          = "index.handler"
  filename         = data.archive_file.add_to_shopper.output_path
  source_code_hash = data.archive_file.add_to_shopper.output_base64sha256
  timeout          = 10
}

resource "aws_lambda_permission" "cognito_invoke" {
  statement_id  = "AllowCognito"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.add_to_shopper.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.main.arn
}
