# Deploying to AWS

> **Status.** The Terraform in [`infra/`](../infra) passes `terraform validate` and `terraform fmt` against the real
> AWS provider. It has **not** been applied to a real AWS account, and the deploy job in
> [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) has not run against one. Expect to fix small things
> on the first real `apply`, and read the plan before you approve it.

## What gets built

```
Internet --HTTP--> Application Load Balancer --8080--> ECS Fargate task (the app) --5432--> RDS PostgreSQL
                                                              |
                                                              +--(IAM task role)--> S3 bucket (uploaded charts)
```

| Piece | Choice | Why |
|---|---|---|
| Container image | **ECR** repository, immutable tags (the git commit), scan on push, keep the last 10 | cheap, private, tag always means the same image |
| Compute | **ECS on Fargate**, 0.5 vCPU / 1 GB, one task | no servers to patch; scales to zero cost by setting the count to 0 |
| Database | **RDS PostgreSQL 16**, `db.t4g.micro`, single-AZ, encrypted, **no public address** | the smallest instance; only the app's security group can connect |
| Files | **S3**, private, public access blocked, SSE-S3 encryption, TLS-only bucket policy | the app talks to it with an IAM role, never a key |
| Secrets | RDS generates the DB password and keeps it in **Secrets Manager**; ECS injects it at start | the password is never in git, Terraform state or the task definition |
| Logs | **CloudWatch Logs**, 14 days retention | cheap, and old logs cost money to keep |
| Entry point | **Application Load Balancer**, health check `/actuator/health` | standard; plain HTTP unless you give it a certificate |
| Network | **default VPC**, public subnets, **no NAT gateway** | a NAT gateway bills every hour it exists; security groups do the protecting instead |

### IAM, least privilege

- **Task execution role** (used by ECS to *start* the container): the AWS-managed ECS execution policy, plus
  `secretsmanager:GetSecretValue` on **one** secret, the database password.
- **Task role** (used by the *running app*): `s3:PutObject/GetObject/DeleteObject` on the uploads bucket only.
- **GitHub deploy role** (assumed by the workflow): push to **this** ECR repository, register task-definition
  revisions, update **this** service, and `iam:PassRole` for exactly the two task roles above, only to ECS.
  It can be assumed only by this repository's `production` environment (see "OIDC" below).

### OIDC: deploying without stored AWS keys

GitHub Actions asks GitHub for a short-lived signed token that says "I am a job of
`laveenh-source/JazzImprovHelper` in the `production` environment". AWS trusts GitHub as an identity provider
and, if the token's *audience* and *subject* match the role's trust policy, hands out temporary credentials
(about an hour). There are **no AWS access keys in GitHub secrets** to leak or rotate.

Because the deploy job declares `environment: production`, the token's subject is
`repo:<owner>/<repo>:environment:production`; the trust policy matches exactly that. A pull request, another
branch or another repository cannot assume the role. In the repository's *Settings > Environments > production*
you can also require a manual approval before any deploy.

### Express Mode, considered

Amazon ECS **Express Mode** creates a Fargate service with a load balancer and a URL from just an image and two
roles, at no extra charge beyond the resources it creates, and Terraform supports it
(`aws_ecs_express_gateway_service`). It would shorten the ECS and ALB parts of this setup. I chose explicit
resources instead because this project also needs a database, a secret, a bucket, a task role and a precise deploy
role wired together, and writing those out is clearer to explain and review. Switching later is possible.

## Costs: what bills even when nothing is happening

I have not put dollar amounts here because prices change and differ by region; look up the current numbers on the
AWS pricing pages (ECS Fargate, RDS for PostgreSQL, Elastic Load Balancing, and CloudWatch) or the
[AWS Pricing Calculator](https://calculator.aws/).

| Resource | Bills while idle? | Notes |
|---|---|---|
| **Application Load Balancer** | **Yes, every hour it exists** | usually the largest fixed cost of a small demo |
| **RDS instance + its storage** | **Yes** (stopping an RDS instance only pauses it for up to 7 days) | the instance hours and the 20 GB of storage |
| **Fargate task** | Only while running | set `desired_count = 0` to stop paying for compute |
| Public IPv4 addresses | **Yes** (each task and the load balancer has one) | AWS charges for public IPv4 addresses |
| S3 bucket, ECR images, CloudWatch logs | Small, grows with data | lifecycle rules and 14-day log retention keep it down |
| Secrets Manager secret | Yes, a small monthly fee | created by RDS for the password |
| NAT gateway | **Not created** | avoided on purpose |
| Budget alert | Free for the first budgets | |

**The cheapest way to pause is `terraform destroy`**, because the load balancer and the database bill even when
the app is scaled to zero.

## Step by step

You need: an AWS account, the AWS CLI logged in (`aws sts get-caller-identity` works), Terraform 1.6+, Docker.

### 0. Set a billing alarm before anything else

Terraform creates a monthly budget with email alerts at 50% and 80% of actual spend and 100% of forecast
(`infra/budget.tf`). Set `alert_email` first. Also turn on, once per account, in the Billing console:
*Billing preferences > Receive billing alerts*, and consider AWS Cost Anomaly Detection. A budget only *warns*;
it does not stop spending, so also remember the teardown below.

### 1. Create the infrastructure

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars      # set alert_email
terraform init
terraform plan                                    # read it
terraform apply                                   # creates everything with the app stopped (desired_count = 0)
```

If your account already has a GitHub OIDC provider (only one is allowed per account), add
`-var create_github_oidc_provider=false`.

### 2. Push the first image, then start the app

The ECS service needs an image to start. This pushes one tagged `bootstrap`, and then starts one task:

```bash
./push-first-image.sh
terraform apply -var desired_count=1
terraform output app_url                          # open it in a browser, or: scripts/smoke-test.sh <url>
```

### 3. Connect GitHub Actions

`terraform output github_variables` prints the values. In GitHub: *Settings > Secrets and variables > Actions >
Variables* (**variables, not secrets**: none of them is sensitive), create `AWS_REGION`, `AWS_DEPLOY_ROLE_ARN`,
`ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE`, `ECS_TASK_FAMILY`, `APP_URL`. Then create the environment
*Settings > Environments > New environment > `production`* (optionally with required reviewers).

From now on every push to `main` that passes the tests deploys itself: build the image, push it tagged with the
commit, register a new task-definition revision, update the service, wait for it to become stable, then run
`scripts/smoke-test.sh` against the live URL. A bad release rolls back automatically (the ECS deployment
circuit breaker).

Until `AWS_DEPLOY_ROLE_ARN` exists, the deploy job is skipped, so CI is green without AWS.

## Tearing it all down

```bash
cd infra
terraform destroy
```

This removes the service, load balancer, database (no final snapshot, by design for a demo), S3 bucket (emptied
first), ECR repository (with its images), logs, roles and the budget. Afterwards check in the console that nothing
is left: *EC2 > Load Balancers*, *RDS > Databases* (and *Snapshots*, in case you took manual ones),
*ECS > Clusters*, *S3*, *Secrets Manager*, and *Billing > Bills* for the following day.

The things Terraform does **not** know about, which you must remove yourself if you created them: manual RDS
snapshots, extra images you pushed by hand to other repositories, and the GitHub variables/environment (harmless
once the role is gone, but tidy).

## Known limits of this setup (be honest about them)

- **Plain HTTP** unless you own a domain and give Terraform an ACM `certificate_arn`. The demo should not take
  anything sensitive; the API has no authentication.
- **Single instance, single AZ database**: one task and one database in one availability zone. No high availability.
- **In-memory analysis queue**: a task restart loses queued work. Stuck analyses are marked FAILED at startup and
  retried by uploading the file again. Fine at this scale; a real queue (SQS) would replace it for more.
- **Per-IP rate limiting is in each task's memory**; with several tasks each keeps its own count.
- **Not load tested on AWS.** See `docs/PERFORMANCE.md` for what was measured, and what was not.
