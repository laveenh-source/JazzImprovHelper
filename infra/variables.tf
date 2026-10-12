variable "region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefix for resource names."
  type        = string
  default     = "jazz-analyzer"
}

variable "github_repo" {
  description = "The GitHub repository allowed to deploy, as owner/name."
  type        = string
  default     = "laveenh-source/JazzImprovHelper"
}

variable "alert_email" {
  description = "Where billing alerts are sent. Required: the budget is the safety net against a surprise bill."
  type        = string
}

variable "monthly_budget_usd" {
  description = "Monthly cost budget. Alerts fire at 50% and 80% of actual spend and at 100% of forecast."
  type        = number
  default     = 20
}

variable "desired_count" {
  description = "How many copies of the app run. 0 = stopped (and no Fargate charge). See docs/AWS_DEPLOYMENT.md for the first-deploy order."
  type        = number
  default     = 0
}

variable "container_cpu" {
  description = "Fargate CPU units (256 = 0.25 vCPU)."
  type        = number
  default     = 512
}

variable "container_memory" {
  description = "Fargate memory in MiB."
  type        = number
  default     = 1024
}

variable "db_instance_class" {
  description = "RDS instance size. db.t4g.micro is the smallest."
  type        = string
  default     = "db.t4g.micro"
}

variable "create_github_oidc_provider" {
  description = "An AWS account can have only one GitHub OIDC provider. Set false if yours already exists (it is then looked up instead)."
  type        = bool
  default     = true
}

variable "certificate_arn" {
  description = "Optional ACM certificate. If set, the load balancer serves HTTPS and redirects HTTP to it. Needs a domain you own; without it the demo is plain HTTP."
  type        = string
  default     = ""
}
