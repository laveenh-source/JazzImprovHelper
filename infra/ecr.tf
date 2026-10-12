resource "aws_ecr_repository" "app" {
  name                 = var.project_name
  image_tag_mutability = "IMMUTABLE" # a tag (the git commit) always means the same image
  force_delete         = true        # lets `terraform destroy` remove it even when it still holds images

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Old images cost storage money: keep only the latest ten.
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 10 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}
