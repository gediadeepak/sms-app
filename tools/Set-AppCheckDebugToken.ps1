#requires -Version 7.0
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory = $true)][string]$AdbPath,
    [string]$Serial,
    [string]$ConfigPath = (Join-Path $PSScriptRoot '..\appcheck-debug.local.properties')
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.sandesh.app'
# Developer-only provisioning for the inspected firebase-appcheck-debug 20.1.0 store.
# No secret is compiled into an APK or passed as an adb command-line argument.
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'Specify the installed Android SDK adb executable.' }
if (-not (Test-Path -LiteralPath $ConfigPath)) { throw 'Create the ignored appcheck-debug.local.properties file with appCheck.debugToken=<registered token>.' }
$config = Get-Content -LiteralPath $ConfigPath
$tokenLines = @($config | Where-Object { $_ -match '^\s*appCheck\.debugToken\s*=' })
if ($tokenLines.Count -ne 1) { throw 'Configure exactly one appCheck.debugToken entry.' }
$debugToken = ($tokenLines[0] -split '=', 2)[1].Trim()
if ($debugToken -notmatch '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') { throw 'The configured debug token must be a UUID.' }

if (-not $Serial) {
    $devices = @(& $AdbPath devices)
    if ($LASTEXITCODE -ne 0) { throw 'Could not list Android devices.' }
    $connected = @($devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($connected.Count -ne 1) { throw 'Connect one authorized Android device or specify -Serial.' }
    $Serial = $connected[0]
}

# run-as is permitted only for a debuggable app; release installations fail here.
$files = @(& $AdbPath -s $Serial shell run-as $packageName ls shared_prefs 2>$null)
if ($LASTEXITCODE -ne 0) { throw 'A running/initialized debuggable Sandesh installation is required. Release builds cannot be provisioned by this helper.' }
$debugStores = @($files | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^com\.google\.firebase\.appcheck\.debug\.store\.[A-Za-z0-9_+=-]+\.xml$' })
if ($debugStores.Count -ne 1) { throw 'Expected one Firebase debug store. Run Sandesh and Check device verification once, then retry.' }
$storePath = 'shared_prefs/' + $debugStores[0]
$xmlText = (@(& $AdbPath -s $Serial exec-out run-as $packageName cat $storePath) -join "`n")
if ($LASTEXITCODE -ne 0) { throw 'Could not read the App Check debug store.' }
$document = [System.Xml.XmlDocument]::new()
$document.LoadXml($xmlText)
$entry = $document.SelectSingleNode('/map/string[@name="com.google.firebase.appcheck.debug.DEBUG_SECRET"]')
if ($null -eq $entry) { throw 'The SDK debug-store layout is unsupported. Use the Logcat registration workflow instead.' }

if (-not $PSCmdlet.ShouldProcess('Connected debuggable Sandesh installation', 'Close app and provision its registered App Check debug token')) { return }
& $AdbPath -s $Serial shell am force-stop $packageName | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not stop Sandesh before updating its debug store.' }
# Read again after stopping so no SDK preference update can race this operation.
$xmlText = (@(& $AdbPath -s $Serial exec-out run-as $packageName cat $storePath) -join "`n")
if ($LASTEXITCODE -ne 0) { throw 'Could not read the stopped application debug store.' }
$document.LoadXml($xmlText)
$entry = $document.SelectSingleNode('/map/string[@name="com.google.firebase.appcheck.debug.DEBUG_SECRET"]')
if ($null -eq $entry) { throw 'The debug store changed. No token was written.' }
$entry.InnerText = $debugToken

# File names are restricted above; the secret travels over stdin, never a shell command.
$start = [System.Diagnostics.ProcessStartInfo]::new()
$start.FileName = $AdbPath
$start.UseShellExecute = $false
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$start.CreateNoWindow = $true
foreach ($argument in @('-s', $Serial, 'shell', "run-as $packageName sh -c 'cat > $storePath.sandesh-tmp && chmod 600 $storePath.sandesh-tmp && mv $storePath.sandesh-tmp $storePath'")) {
    $start.ArgumentList.Add($argument)
}
$writer = [System.Diagnostics.Process]::Start($start)
$writer.StandardInput.Write($document.OuterXml)
$writer.StandardInput.Close()
$null = $writer.StandardOutput.ReadToEnd()
$null = $writer.StandardError.ReadToEnd()
$writer.WaitForExit()
if ($writer.ExitCode -ne 0) { $writer.Dispose(); throw 'Token provisioning failed. The app can regenerate a token using the normal SDK workflow.' }
$writer.Dispose()

$readback = (@(& $AdbPath -s $Serial exec-out run-as $packageName cat $storePath) -join "`n")
if ($LASTEXITCODE -ne 0) { throw 'Could not verify the written debug store.' }
$verified = [System.Xml.XmlDocument]::new()
$verified.LoadXml($readback)
$saved = $verified.SelectSingleNode('/map/string[@name="com.google.firebase.appcheck.debug.DEBUG_SECRET"]')
if ($null -eq $saved -or $saved.InnerText -cne $debugToken) { throw 'The saved token did not match the private configuration.' }
Write-Output 'Configured and verified the registered token on the debug installation. No token was printed or embedded in an APK.'
Write-Output 'Reopen Sandesh and tap Settings > AI assistance > Check device verification.'
