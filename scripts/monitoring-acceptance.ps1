param(
    [string]$AppBaseUri = 'http://localhost:8080',
    [string]$PrometheusBaseUri = 'http://localhost:9090',
    [string]$GrafanaBaseUri = 'http://localhost:3000',
    [int]$TimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'

function Wait-Until {
    param(
        [scriptblock]$Condition,
        [string]$FailureMessage
    )

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            if (& $Condition) { return }
        } catch {
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw $FailureMessage
}

Wait-Until -FailureMessage 'Application Prometheus endpoint did not become ready' -Condition {
    $metrics = (Invoke-WebRequest -Uri "$AppBaseUri/actuator/prometheus" -TimeoutSec 5).Content
    return $metrics -match 'jvm_memory_used_bytes' -and
        $metrics -match 'knowledge_ingestion_tasks_total' -and
        $metrics -match 'knowledge_ingestion_queue_size'
}

Wait-Until -FailureMessage 'Prometheus did not report the application target as up' -Condition {
    $targets = Invoke-RestMethod -Uri "$PrometheusBaseUri/api/v1/targets" -TimeoutSec 5
    return @($targets.data.activeTargets | Where-Object {
        $_.labels.job -eq 'newideacase-app' -and $_.health -eq 'up'
    }).Count -eq 1
}

$grafanaHealth = Invoke-RestMethod -Uri "$GrafanaBaseUri/api/health" -TimeoutSec 5
if ($grafanaHealth.database -ne 'ok') {
    throw "Grafana database health is $($grafanaHealth.database)"
}

$grafanaUser = (docker compose exec -T grafana printenv GF_SECURITY_ADMIN_USER).Trim()
$grafanaPassword = (docker compose exec -T grafana printenv GF_SECURITY_ADMIN_PASSWORD).Trim()
$credential = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${grafanaUser}:${grafanaPassword}"))
$headers = @{ Authorization = "Basic $credential" }

Wait-Until -FailureMessage 'Grafana dashboard was not provisioned' -Condition {
    $dashboard = Invoke-RestMethod -Uri "$GrafanaBaseUri/api/dashboards/uid/newideacase-platform" `
        -Headers $headers -TimeoutSec 5
    return $dashboard.dashboard.uid -eq 'newideacase-platform'
}

$prometheusUp = Invoke-RestMethod -Uri `
    "$PrometheusBaseUri/api/v1/query?query=up%7Bjob%3D%22newideacase-app%22%7D" -TimeoutSec 5

[pscustomobject]@{
    result = 'PASS'
    applicationMetrics = $true
    prometheusTarget = $prometheusUp.data.result[0].value[1]
    grafanaDatabase = $grafanaHealth.database
    grafanaDashboard = 'newideacase-platform'
} | ConvertTo-Json -Compress
