param(
    [switch]$SkipExp2,
    [switch]$SkipExp5,
    [string]$DbUser = "root",
    [string]$DbPass = "123456",
    [string]$DbHost = "127.0.0.1"
)

$ErrorActionPreference = "Continue"
$rootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $rootDir

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logsDir = Join-Path $rootDir "reports"
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir | Out-Null }

function Write-Step {
    param([string]$Title)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host " $Title" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}

function Check-Success {
    param([string]$LogFile, [string]$Label)
    if (Test-Path $LogFile) {
        $content = Get-Content $LogFile -Raw
        if ($content -match "BUILD SUCCESS") {
            Write-Host "  [OK] $Label" -ForegroundColor Green
            return $true
        } else {
            Write-Host "  [FAIL] $Label" -ForegroundColor Red
            return $false
        }
    }
    return $false
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Yellow
Write-Host " checkdata-agent Quant Experiments" -ForegroundColor Yellow
Write-Host " Start: $timestamp" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
Write-Host ""

Write-Step "Step 0/5: Env Check"
$java = java -version 2>&1 | Select-Object -First 1
Write-Host "  JDK: $java"
$mvnVer = mvn -version 2>&1 | Select-Object -First 1
Write-Host "  Maven: $mvnVer"
Write-Host "  MySQL: $DbHost`:3306"

Write-Step "Step 1/5: Compile"
mvn clean compile test-compile --batch-mode 2>&1 | Out-File -FilePath "$logsDir\compile.log" -Encoding utf8
if (-not (Check-Success "$logsDir\compile.log" "Compile")) { exit 1 }

Write-Step "Step 2/5: Exp1 - SqlSafetyGuard + Exp4 - DataScopeRewriter + Exp3 - SchemaStats"
mvn test "-Dtest=SqlSafetyGuardTest,DataScopeRewriterTest,SchemaStatsTest" --batch-mode 2>&1 | Out-File -FilePath "$logsDir\exp143_output.log" -Encoding utf8
Check-Success "$logsDir\exp143_output.log" "Exp1+4+3"

$exp1Metrics = Select-String -Path "$logsDir\exp143_output.log" -Pattern "blocked \d+|passed \d+|LIMIT injected|Tables:|FKs:|coverage:"
if ($exp1Metrics) {
    Write-Host "  Key Metrics:" -ForegroundColor Gray
    $exp1Metrics | ForEach-Object { Write-Host "    $($_.Line.Trim())" -ForegroundColor Gray }
}

Write-Step "Step 3/5: Exp5 - LatencyProfile"
mvn test -Dtest=LatencyProfileTest --batch-mode 2>&1 | Out-File -FilePath "$logsDir\exp5_output.log" -Encoding utf8
Check-Success "$logsDir\exp5_output.log" "Exp5"

if (-not $SkipExp2) {
    Write-Step "Step 4/5: Exp2 - ExplainPrecheck"
    Write-Host "  [Note] Run this first to build big table:" -ForegroundColor Yellow
    Write-Host "  mysql -u$DbUser -p$DbPass checkdata_agent -h$DbHost -e `"source scripts/量化数据/gen_big_table.sql`"" -ForegroundColor Yellow
    Write-Host "  Then run: mvn test -Dtest=ExplainPrecheckTest" -ForegroundColor Yellow
}

Write-Step "Full Summary"
mvn test --batch-mode 2>&1 | Out-File -FilePath "$logsDir\all_output.log" -Encoding utf8

$summary = Select-String -Path "$logsDir\all_output.log" -Pattern "^Tests run: \d+ Failures: \d+ Errors: \d+" | Select-Object -Last 1
if ($summary) {
    Write-Host "  $($summary.Line.Trim())" -ForegroundColor Cyan
}

$buildResult = Select-String -Path "$logsDir\all_output.log" -Pattern "BUILD SUCCESS|BUILD FAILURE" | Select-Object -Last 1
if ($buildResult) {
    $color = if ($buildResult.Line -match "SUCCESS") { "Green" } else { "Red" }
    Write-Host "  $($buildResult.Line.Trim())" -ForegroundColor $color
}

Write-Host ""
Write-Host "Done: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "Logs saved to: $logsDir"
Write-Host ""