param(
    [string]$GatewayBaseUri = 'https://localhost',
    [string]$KeycloakBaseUri = 'http://localhost:8180',
    [string]$SecureAppBaseUri = 'http://localhost:8081',
    [string]$MailpitBaseUri = 'http://localhost:8025',
    [string]$AlertmanagerBaseUri = 'http://localhost:9093',
    [string]$LokiBaseUri = 'http://localhost:3100',
    [string]$PrometheusBaseUri = 'http://localhost:9090',
    [string]$GrafanaBaseUri = 'http://localhost:3000',
    [string]$PortainerBaseUri = 'http://localhost:9000',
    [string]$PortainerAdminUser = 'admin',
    [string]$PortainerAdminPassword = 'local-dev-portainer-password',
    [int]$TimeoutSeconds = 120
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
            if (& $Condition) {
                return
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw $FailureMessage
}

function Read-JwtPayload {
    param([string]$Token)

    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) |
        ConvertFrom-Json
}

Wait-Until -FailureMessage 'HTTPS gateway did not become ready' -Condition {
    (Invoke-RestMethod -Uri "$GatewayBaseUri/actuator/health" `
        -SkipCertificateCheck -TimeoutSec 5).status -eq 'UP'
}

$redirectHeaders = (& curl.exe -sS -D - -o NUL 'http://localhost:8088/actuator/health') -join "`n"
if ($LASTEXITCODE -ne 0 -or
    $redirectHeaders -notmatch 'HTTP/\d(?:\.\d)? 308' -or
    $redirectHeaders -notmatch 'Location: https://localhost/actuator/health') {
    throw 'HTTP gateway did not return the expected HTTPS redirect'
}

Wait-Until -FailureMessage 'Keycloak realm did not become ready' -Condition {
    $realm = Invoke-RestMethod -Uri "$KeycloakBaseUri/realms/newideacase" -TimeoutSec 5
    return $realm.realm -eq 'newideacase'
}

$tokenResponse = Invoke-RestMethod -Method Post `
    -Uri "$KeycloakBaseUri/realms/newideacase/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{
        grant_type = 'password'
        client_id = 'new-idea-case-api'
        username = 'api-tester'
        password = 'local-dev-api-password'
    } -TimeoutSec 10

$claims = Read-JwtPayload -Token $tokenResponse.access_token
if ($claims.iss -ne 'http://identity.localhost:8180/realms/newideacase' -or
    $claims.tenant_id -ne 'local' -or
    @($claims.aud) -notcontains 'new-idea-case-api' -or
    $claims.scope -notmatch 'catalog\.read' -or
    $claims.scope -notmatch 'profile\.write' -or
    $claims.scope -notmatch 'orders\.write' -or
    @($claims.roles) -notcontains 'support') {
    throw 'Keycloak token is missing the expected issuer, audience, scope, tenant, or roles'
}

$unauthorized = Invoke-WebRequest -Uri "$SecureAppBaseUri/api/v1/products?limit=1" `
    -SkipHttpErrorCheck -TimeoutSec 5
if ($unauthorized.StatusCode -ne 401) {
    throw "Secure application returned $($unauthorized.StatusCode) instead of 401 without a token"
}

$secureProducts = Invoke-RestMethod -Uri "$SecureAppBaseUri/api/v1/products?limit=1" `
    -Headers @{ Authorization = "Bearer $($tokenResponse.access_token)" } -TimeoutSec 10
if ($null -eq $secureProducts.items) {
    throw 'Secure application did not return a product page for the Keycloak token'
}

$authorization = @{ Authorization = "Bearer $($tokenResponse.access_token)" }
$profileBody = @{
    displayName = 'API Tester'
    locale = 'zh-TW'
    timeZone = 'Asia/Taipei'
} | ConvertTo-Json
$profile = Invoke-RestMethod -Method Put -Uri "$SecureAppBaseUri/api/v1/users/me" `
    -Headers $authorization -ContentType 'application/json' -Body $profileBody -TimeoutSec 10
$readProfile = Invoke-RestMethod -Uri "$SecureAppBaseUri/api/v1/users/me" `
    -Headers $authorization -TimeoutSec 10
if ($profile.userId -ne $claims.sub -or $readProfile.timeZone -ne 'Asia/Taipei') {
    throw 'Secure user profile did not preserve the JWT subject or profile fields'
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$productBody = @{
    sku = "SEC-$suffix"
    name = 'Secure order product'
    amount = 25.50
    currency = 'TWD'
} | ConvertTo-Json
$orderProduct = Invoke-RestMethod -Method Post -Uri "$SecureAppBaseUri/api/v1/products" `
    -Headers $authorization -ContentType 'application/json' -Body $productBody -TimeoutSec 10

$orderBody = @{
    items = @(
        @{
            productId = $orderProduct.id
            quantity = 2
        }
    )
} | ConvertTo-Json -Depth 4
$order = Invoke-RestMethod -Method Post -Uri "$SecureAppBaseUri/api/v1/orders" `
    -Headers $authorization -ContentType 'application/json' -Body $orderBody -TimeoutSec 10
if ($order.status -ne 'PENDING' -or $order.totalAmount -ne 51.00 -or
    $order.items[0].sku -ne $orderProduct.sku) {
    throw 'Secure order did not preserve the product snapshot, quantity, or total'
}

$statusBody = @{
    status = 'CONFIRMED'
    version = $order.version
} | ConvertTo-Json
$confirmedOrder = Invoke-RestMethod -Method Patch `
    -Uri "$SecureAppBaseUri/api/v1/orders/$($order.id)/status" `
    -Headers $authorization -ContentType 'application/json' -Body $statusBody -TimeoutSec 10
if ($confirmedOrder.status -ne 'CONFIRMED') {
    throw 'Secure order status did not transition to CONFIRMED'
}

$staleOrder = Invoke-WebRequest -Method Patch `
    -Uri "$SecureAppBaseUri/api/v1/orders/$($order.id)/status" `
    -Headers $authorization -ContentType 'application/json' -Body $statusBody `
    -SkipHttpErrorCheck -TimeoutSec 10
if ($staleOrder.StatusCode -ne 409) {
    throw "Stale order transition returned $($staleOrder.StatusCode) instead of 409"
}

Wait-Until -FailureMessage 'Mailpit did not become ready' -Condition {
    (Invoke-WebRequest -Uri "$MailpitBaseUri/livez" -TimeoutSec 5).StatusCode -eq 200
}

Wait-Until -FailureMessage 'Alertmanager did not become ready' -Condition {
    (Invoke-WebRequest -Uri "$AlertmanagerBaseUri/-/ready" -TimeoutSec 5).StatusCode -eq 200
}

$alertName = "PlatformAcceptance$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$startsAt = [DateTimeOffset]::UtcNow.ToString('o')
$endsAt = [DateTimeOffset]::UtcNow.AddMinutes(2).ToString('o')
$alert = @{
    labels = @{
        alertname = $alertName
        severity = 'info'
    }
    annotations = @{
        summary = 'Local platform service acceptance alert'
    }
    startsAt = $startsAt
    endsAt = $endsAt
} | ConvertTo-Json -Depth 5 -AsArray

Invoke-RestMethod -Method Post -Uri "$AlertmanagerBaseUri/api/v2/alerts" `
    -ContentType 'application/json' -Body $alert -TimeoutSec 10 | Out-Null

Wait-Until -FailureMessage 'Mailpit did not receive the Alertmanager acceptance email' -Condition {
    $messages = Invoke-RestMethod -Uri "$MailpitBaseUri/api/v1/messages" -TimeoutSec 5
    return @($messages.messages | Where-Object { $_.Subject -like "*$alertName*" }).Count -gt 0
}

Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 5 | Out-Null

Wait-Until -FailureMessage 'Loki did not receive the application log stream' -Condition {
    $query = [Uri]::EscapeDataString('{job="newideacase-app"}')
    $end = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000
    $start = [DateTimeOffset]::UtcNow.AddMinutes(-15).ToUnixTimeMilliseconds() * 1000000
    $response = Invoke-RestMethod `
        -Uri "$LokiBaseUri/loki/api/v1/query_range?query=$query&start=$start&end=$end&limit=20" `
        -TimeoutSec 5
    return @($response.data.result).Count -gt 0
}

$grafanaHealth = Invoke-RestMethod -Uri "$GrafanaBaseUri/api/health" -TimeoutSec 5
$grafanaUser = (docker compose exec -T grafana printenv GF_SECURITY_ADMIN_USER).Trim()
$grafanaPassword = (docker compose exec -T grafana printenv GF_SECURITY_ADMIN_PASSWORD).Trim()
$grafanaCredential = [Convert]::ToBase64String(
    [Text.Encoding]::UTF8.GetBytes("${grafanaUser}:${grafanaPassword}"))
$lokiDatasource = Invoke-RestMethod -Uri "$GrafanaBaseUri/api/datasources/uid/loki" `
    -Headers @{ Authorization = "Basic $grafanaCredential" } -TimeoutSec 5
if ($grafanaHealth.database -ne 'ok' -or $lokiDatasource.type -ne 'loki') {
    throw 'Grafana is unhealthy or the Loki datasource was not provisioned'
}

Wait-Until -FailureMessage 'Portainer API did not become ready' -Condition {
    (Invoke-RestMethod -Uri "$PortainerBaseUri/api/system/status" -TimeoutSec 5).Version
}

$portainerCredentials = @{
    Username = $PortainerAdminUser
    Password = $PortainerAdminPassword
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post -Uri "$PortainerBaseUri/api/users/admin/init" `
        -ContentType 'application/json' -Body $portainerCredentials -TimeoutSec 5 | Out-Null
} catch {
    if ($_.Exception.Response.StatusCode.value__ -notin 409, 422) {
        throw
    }
}

$portainerAuth = Invoke-RestMethod -Method Post -Uri "$PortainerBaseUri/api/auth" `
    -ContentType 'application/json' -Body $portainerCredentials -TimeoutSec 5
if ([string]::IsNullOrWhiteSpace($portainerAuth.jwt)) {
    throw 'Portainer authentication did not return a JWT'
}

Wait-Until -FailureMessage 'Prometheus did not report Alertmanager and Loki targets as up' -Condition {
    $targets = Invoke-RestMethod -Uri "$PrometheusBaseUri/api/v1/targets" -TimeoutSec 5
    $upJobs = @($targets.data.activeTargets |
        Where-Object { $_.health -eq 'up' } |
        ForEach-Object { $_.labels.job })
    return $upJobs -contains 'alertmanager' -and $upJobs -contains 'loki'
}

[pscustomobject]@{
    result = 'PASS'
    httpsGateway = $true
    keycloakRealm = 'newideacase'
    secureApi = $true
    userProfile = $profile.id
    order = $confirmedOrder.id
    staleOrderStatus = $staleOrder.StatusCode
    alertEmail = $true
    lokiLogs = $true
    grafanaLokiDatasource = $lokiDatasource.uid
    portainerVersion = (Invoke-RestMethod -Uri "$PortainerBaseUri/api/system/status").Version
    prometheusTargets = @('alertmanager', 'loki')
} | ConvertTo-Json -Compress
