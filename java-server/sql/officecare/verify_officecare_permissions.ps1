[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$controllerRoot = Join-Path $repoRoot 'java-server\ruoyi-admin\src\main\java\com\ruoyi\web\controller\officecare'
$sqlRoot = Join-Path $repoRoot 'java-server\sql\officecare'
$viewRoot = Join-Path $repoRoot 'web-admin\src\views\officecare'
$roleMatrixPath = Join-Path $sqlRoot '014_officecare_role_permissions.sql'
$permissionPattern = 'officecare:[a-z-]+:[a-z-]+'

function Get-PermissionSet {
    param([System.IO.FileInfo[]]$Files)

    $result = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($file in $Files) {
        $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        foreach ($match in [regex]::Matches($content, $permissionPattern)) {
            [void]$result.Add($match.Value)
        }
    }
    return $result
}

$controllerFiles = @(Get-ChildItem -LiteralPath $controllerRoot -Filter '*.java' -File)
$sqlFiles = @(Get-ChildItem -LiteralPath $sqlRoot -Filter '*.sql' -File)
$viewFiles = @(Get-ChildItem -LiteralPath $viewRoot -Filter '*.vue' -File -Recurse)

if (-not $controllerFiles.Count -or -not $sqlFiles.Count -or -not $viewFiles.Count) {
    throw 'OfficeCare permission audit directories are incomplete.'
}

$endpointCount = 0
$preAuthorizeCount = 0
$unprotectedControllers = [System.Collections.Generic.List[string]]::new()
foreach ($file in $controllerFiles) {
    $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $fileEndpointCount = [regex]::Matches(
        $content,
        '@(?:Get|Post|Put|Delete|Patch)Mapping'
    ).Count
    $filePreAuthorizeCount = [regex]::Matches($content, '@PreAuthorize').Count
    $endpointCount += $fileEndpointCount
    $preAuthorizeCount += $filePreAuthorizeCount
    if ($fileEndpointCount -ne $filePreAuthorizeCount) {
        $unprotectedControllers.Add(
            "$($file.Name): endpoints=$fileEndpointCount, preAuthorize=$filePreAuthorizeCount"
        )
    }
}

if ($unprotectedControllers.Count) {
    throw "Controllers are not fully protected:`n$($unprotectedControllers -join "`n")"
}

$backendPermissions = Get-PermissionSet -Files $controllerFiles
$sqlPermissions = Get-PermissionSet -Files $sqlFiles
$frontendPermissions = Get-PermissionSet -Files $viewFiles

$backendMissingFromSql = @(
    $backendPermissions |
        Where-Object { -not $sqlPermissions.Contains($_) } |
        Sort-Object
)
if ($backendMissingFromSql.Count) {
    throw "Backend permissions missing from SQL: $($backendMissingFromSql -join ', ')"
}

$frontendUnknown = @(
    $frontendPermissions |
        Where-Object { -not $backendPermissions.Contains($_) } |
        Sort-Object
)
if ($frontendUnknown.Count) {
    throw "Frontend permissions missing from backend: $($frontendUnknown -join ', ')"
}

$requiredFrontendActions = @(
    'officecare:dashboard:query',
    'officecare:brand:add',
    'officecare:brand:edit',
    'officecare:brand:remove',
    'officecare:model:add',
    'officecare:model:edit',
    'officecare:model:remove',
    'officecare:knowledge-base:add',
    'officecare:knowledge-base:edit',
    'officecare:knowledge-base:remove',
    'officecare:document:query',
    'officecare:document:upload',
    'officecare:document:edit',
    'officecare:document:remove',
    'officecare:document:parse',
    'officecare:document:index',
    'officecare:document:download',
    'officecare:customer:add',
    'officecare:customer:edit',
    'officecare:customer:remove',
    'officecare:device:add',
    'officecare:device:edit',
    'officecare:device:remove',
    'officecare:workorder:add',
    'officecare:workorder:edit',
    'officecare:workorder:assign',
    'officecare:workorder:status',
    'officecare:workorder:record',
    'officecare:workorder:ai',
    'officecare:workorder:remove',
    'officecare:agent:query',
    'officecare:agent:analyze',
    'officecare:agent:edit',
    'officecare:agent:confirm',
    'officecare:agent:remove'
)
$missingFrontendActions = @(
    $requiredFrontendActions |
        Where-Object { -not $frontendPermissions.Contains($_) }
)
if ($missingFrontendActions.Count) {
    throw "Frontend action permissions are missing: $($missingFrontendActions -join ', ')"
}

if (-not (Test-Path -LiteralPath $roleMatrixPath)) {
    throw 'The final OfficeCare role matrix SQL is missing.'
}
$roleMatrix = Get-Content -LiteralPath $roleMatrixPath -Raw -Encoding UTF8
$requiredRoleMatrixMarkers = @(
    "'oc_knowledge'",
    "'oc_service'",
    "r.role_key in ('oc_knowledge', 'oc_service')",
    "m.perms like 'officecare:document:%'",
    "m.perms like 'officecare:workorder:%'",
    'delete rm',
    'join tmp_oc_permission_menu t on t.menu_id = rm.menu_id'
)
$missingRoleMarkers = @(
    $requiredRoleMatrixMarkers |
        Where-Object { -not $roleMatrix.Contains($_) }
)
if ($missingRoleMarkers.Count) {
    throw "Role matrix SQL markers are missing: $($missingRoleMarkers -join ', ')"
}

Write-Output 'OfficeCare permission static audit passed.'
Write-Output "Controllers: $($controllerFiles.Count)"
Write-Output "Endpoints and PreAuthorize: $endpointCount/$preAuthorizeCount"
Write-Output "Backend permission tokens: $($backendPermissions.Count)"
Write-Output "Frontend permission tokens: $($frontendPermissions.Count)"
Write-Output 'Role matrix: built-in admin + oc_knowledge + oc_service'
