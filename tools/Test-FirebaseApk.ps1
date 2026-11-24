[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$Aapt2Path
)

$ErrorActionPreference = 'Stop'
$resourceDump = @(& $Aapt2Path dump resources $ApkPath)
if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect APK resources.' }

# These names must survive shrinking because Firebase and account setup resolve them at runtime.
$requiredNames = @('google_app_id', 'google_api_key', 'project_id', 'gcm_defaultSenderId', 'default_web_client_id')
foreach ($resourceName in $requiredNames) {
    if (-not ($resourceDump | Select-String -Quiet -Pattern ("\bstring/" + [regex]::Escape($resourceName) + "(?:\s|$)"))) {
        throw "Required Firebase resource is missing from the APK: $resourceName"
    }
}
Write-Output 'Verified Firebase and Google sign-in resources in the APK.'
