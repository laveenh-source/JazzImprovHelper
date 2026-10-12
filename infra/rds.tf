resource "aws_db_subnet_group" "db" {
  name       = "${var.project_name}-db"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "db" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "jazz"
  username = "jazz"
  # RDS generates the password, stores it in AWS Secrets Manager and can rotate it. It never appears in this
  # repository, in Terraform variables, or in the Terraform state.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.db.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false # the smallest, cheapest setup: fine for a demo, not for production

  backup_retention_period    = 1
  auto_minor_version_upgrade = true
  apply_immediately          = true

  # Demo settings that make `terraform destroy` clean. For anything real, turn both of these around.
  skip_final_snapshot = true
  deletion_protection = false
}
