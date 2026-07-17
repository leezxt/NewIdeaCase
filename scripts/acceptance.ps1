param(
    [string]$BaseUri = 'http://localhost:8080',
    [int]$ProcessingTimeoutSeconds = 20
)

$ErrorActionPreference = 'Stop'

function Invoke-ExpectedStatus {
    param(
        [string]$Method,
        [string]$Uri,
        [string]$Body,
        [int]$ExpectedStatus
    )

    $status = 0
    try {
        Invoke-WebRequest -Method $Method -Uri $Uri -ContentType 'application/json' -Body $Body | Out-Null
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
    }
    if ($status -ne $ExpectedStatus) {
        throw "Expected HTTP $ExpectedStatus from $Uri, got $status"
    }
}

$health = Invoke-RestMethod -Uri "$BaseUri/actuator/health"
if ($health.status -ne 'UP') {
    throw "Application health is $($health.status)"
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$products = 1..3 | ForEach-Object {
    $body = @{
        sku = "ACCEPT-$suffix-$_"
        name = "Acceptance product $_"
        description = 'Clearable acceptance description'
        amount = 100 + $_
        currency = 'TWD'
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$BaseUri/api/v1/products" `
        -ContentType 'application/json' -Body $body
}

$firstPage = Invoke-RestMethod -Uri "$BaseUri/api/v1/products?limit=2"
if ([string]::IsNullOrWhiteSpace($firstPage.nextCursor)) {
    throw 'Product list did not return nextCursor'
}
$cursor = [Uri]::EscapeDataString($firstPage.nextCursor)
$secondPage = Invoke-RestMethod -Uri "$BaseUri/api/v1/products?limit=2&cursor=$cursor"
$firstIds = @($firstPage.items | ForEach-Object id)
$secondIds = @($secondPage.items | ForEach-Object id)
$overlap = @($firstIds | Where-Object { $secondIds -contains $_ })
if ($overlap.Count -ne 0) {
    throw "Product cursor pages overlap: $($overlap -join ',')"
}

$target = $products[0]
$null = Invoke-RestMethod -Uri "$BaseUri/api/v1/products/$($target.id)"
$clearBody = @{ clearDescription = $true; version = $target.version } | ConvertTo-Json
$cleared = Invoke-RestMethod -Method Patch -Uri "$BaseUri/api/v1/products/$($target.id)" `
    -ContentType 'application/json' -Body $clearBody
if ($null -ne $cleared.description -or $cleared.version -ne ($target.version + 1)) {
    throw 'Product clearDescription or optimistic version verification failed'
}
$staleBody = @{ name = 'Stale update'; version = $target.version } | ConvertTo-Json
Invoke-ExpectedStatus -Method Patch -Uri "$BaseUri/api/v1/products/$($target.id)" `
    -Body $staleBody -ExpectedStatus 409

$readBack = Invoke-RestMethod -Uri "$BaseUri/api/v1/products/$($target.id)"
$redisPassword = (docker compose exec -T app printenv REDIS_PASSWORD).Trim()
$redisResult = @(docker compose exec -T redis redis-cli --no-auth-warning `
    -a $redisPassword EXISTS "products::$($target.id)")
if ([int]$redisResult[-1] -ne 1 -or $readBack.version -ne $cleared.version) {
    throw 'Redis cache acceptance verification failed'
}

$paragraph = ('Deterministic knowledge content. ' * 80).Trim()
$documentBody = @{
    title = "Acceptance policy $suffix"
    mediaType = 'text/plain'
    content = $paragraph
} | ConvertTo-Json
$documentResponse = Invoke-WebRequest -Method Post -Uri "$BaseUri/api/v1/knowledge/documents" `
    -ContentType 'application/json' -Body $documentBody
if ($documentResponse.StatusCode -ne 202) {
    throw "Knowledge registration returned $($documentResponse.StatusCode)"
}
$document = $documentResponse.Content | ConvertFrom-Json
if ($document.PSObject.Properties.Name -contains 'content') {
    throw 'Knowledge registration response exposed source content'
}
$retry = Invoke-RestMethod -Method Post -Uri "$BaseUri/api/v1/knowledge/documents" `
    -ContentType 'application/json' -Body $documentBody
if ($retry.id -ne $document.id) {
    throw 'Knowledge registration retry was not idempotent'
}

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($ProcessingTimeoutSeconds)
do {
    $processed = Invoke-RestMethod -Uri "$BaseUri/api/v1/knowledge/documents/$($document.id)"
    if ($processed.status -eq 'CHUNKED') { break }
    Start-Sleep -Milliseconds 250
} while ([DateTimeOffset]::UtcNow -lt $deadline)
if ($processed.status -ne 'CHUNKED' -or $processed.chunkCount -lt 2) {
    throw "Knowledge document did not reach multi-chunk CHUNKED state: $($processed.status)"
}
if ($processed.PSObject.Properties.Name -contains 'content') {
    throw 'Knowledge status response exposed source content'
}

$databaseCheck = "const id='$($document.id)';" +
    "const d=db.knowledge_documents.findOne({_id:ObjectId(id)});" +
    "const chunks=db.knowledge_chunks.countDocuments({documentId:id,documentVersion:1});" +
    "const task=db.knowledge_ingestion_tasks.findOne({documentId:id});" +
    "if(!d||d.status!=='CHUNKED'||chunks!==d.chunkCount||task.status!=='COMPLETED') quit(2);"
docker compose exec -T mongodb mongosh --quiet new_idea_case --eval $databaseCheck | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'MongoDB document/chunk/outbox acceptance verification failed'
}

$invalidDocument = @{ title = 'Invalid'; mediaType = 'application/pdf'; content = 'x' } | ConvertTo-Json
Invoke-ExpectedStatus -Method Post -Uri "$BaseUri/api/v1/knowledge/documents" `
    -Body $invalidDocument -ExpectedStatus 400
Invoke-ExpectedStatus -Method Post -Uri "$BaseUri/api/v1/rag/answers" `
    -Body '{"question":"acceptance"}' -ExpectedStatus 503

$openApi = Invoke-RestMethod -Uri "$BaseUri/v3/api-docs"
if ($null -eq $openApi.paths.'/api/v1/knowledge/documents'.post -or
    $null -eq $openApi.paths.'/api/v1/knowledge/documents/{id}'.get -or
    $null -eq $openApi.paths.'/api/v1/products/{id}'.patch) {
    throw 'OpenAPI acceptance verification failed'
}

[pscustomobject]@{
    result = 'PASS'
    health = $health.status
    productCursorOverlap = $overlap.Count
    optimisticConflictStatus = 409
    redisCacheKey = 1
    documentId = $document.id
    documentStatus = $processed.status
    chunkCount = $processed.chunkCount
    idempotentRetry = $retry.id -eq $document.id
    sourceContentExposed = $false
    ragDisabledStatus = 503
} | ConvertTo-Json -Compress
