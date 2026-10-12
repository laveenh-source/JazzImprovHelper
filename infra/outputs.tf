output "app_url" {
  description = "Where the app is reachable."
  value       = "${var.certificate_arn == "" ? "http" : "https"}://${aws_lb.app.dns_name}"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "github_variables" {
  description = "Create these as repository VARIABLES (not secrets) in GitHub: Settings > Secrets and variables > Actions > Variables."
  value = {
    AWS_REGION          = var.region
    AWS_DEPLOY_ROLE_ARN = aws_iam_role.github_deploy.arn
    ECR_REPOSITORY      = aws_ecr_repository.app.name
    ECS_CLUSTER         = aws_ecs_cluster.main.name
    ECS_SERVICE         = aws_ecs_service.app.name
    ECS_TASK_FAMILY     = aws_ecs_task_definition.app.family
    APP_URL             = "${var.certificate_arn == "" ? "http" : "https"}://${aws_lb.app.dns_name}"
  }
}

output "uploads_bucket" {
  value = aws_s3_bucket.uploads.bucket
}

output "database_endpoint" {
  value = aws_db_instance.db.address
}
