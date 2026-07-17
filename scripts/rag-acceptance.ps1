param(
    [string]$AppBaseUri = 'http://localhost:8082',
    [int]$TimeoutSeconds = 180
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

Wait-Until -FailureMessage 'RAG application did not become ready' -Condition {
    (Invoke-RestMethod -Uri "$AppBaseUri/actuator/health" -TimeoutSec 5).status -eq 'UP'
}

$marker = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$fact = "Project Cedar acceptance marker $marker uses launch code ORBIT-731."
$documentBody = @{
    title = "Project Cedar $marker"
    mediaType = 'text/plain'
    content = $fact
} | ConvertTo-Json

$document = Invoke-RestMethod -Method Post `
    -Uri "$AppBaseUri/api/v1/knowledge/documents" `
    -ContentType 'application/json' -Body $documentBody -TimeoutSec 10

Wait-Until -FailureMessage 'RAG acceptance document was not chunked' -Condition {
    $script:documentStatus = Invoke-RestMethod `
        -Uri "$AppBaseUri/api/v1/knowledge/documents/$($document.id)" -TimeoutSec 5
    return $script:documentStatus.status -eq 'CHUNKED'
}

$answerBody = @{
    question = "What launch code is used by Project Cedar marker $marker?"
} | ConvertTo-Json
$answer = Invoke-RestMethod -Method Post -Uri "$AppBaseUri/api/v1/rag/answers" `
    -ContentType 'application/json' -Body $answerBody -TimeoutSec $TimeoutSeconds

if ($answer.answer -notmatch 'ORBIT-731' -or
    @($answer.citations | Where-Object { $_.documentId -eq $document.id }).Count -eq 0 -or
    $answer.model -ne 'qwen2.5:0.5b') {
    throw 'Local RAG answer did not preserve the grounded fact, citation, or model name'
}

[pscustomobject]@{
    result = 'PASS'
    model = $answer.model
    embedding = 'nomic-embed-text'
    documentId = $document.id
    documentStatus = $script:documentStatus.status
    groundedFact = $true
    citation = $document.id
} | ConvertTo-Json -Compress
