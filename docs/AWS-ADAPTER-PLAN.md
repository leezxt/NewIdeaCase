# AWS Adapter Plan

AWS is intentionally not connected in the first implementation. Core application modules must not depend on the AWS SDK.

## Planned mapping

| Application boundary | Future AWS adapter |
|---|---|
| `ObjectStoragePort` | S3 presigned URL adapter |
| Container image | ECR |
| Spring Boot runtime | ECS Fargate initially; EKS only if operational needs justify it |
| HTTPS ingress | ALB + ACM |
| Redis | ElastiCache for Redis/Valkey |
| MongoDB | MongoDB Atlas deployed in the selected AWS region |
| Secrets | Secrets Manager or SSM Parameter Store |
| Logs and metrics | CloudWatch and/or managed Prometheus/Grafana |

## Activation rules

- `app.aws.enabled` remains `false` until an AWS environment is provisioned.
- Add the AWS SDK only inside an infrastructure adapter module when S3 or another AWS service is implemented.
- Prefer workload identity/task roles. Do not add static AWS access keys to application configuration.
- Keep region, bucket, endpoints, and resource identifiers externalized.
- Test adapters against a dedicated development account before enabling production profiles.
