<#
.SYNOPSIS
  Build a signed Android APK from plain Java sources with no Gradle and no Android Studio.
.DESCRIPTION
  Pipeline (this is the only order that works):
    aapt2 compile -> aapt2 link -> javac -> d8 -> inject classes.dex -> zipalign -> apksigner -> verify
  Requires an Android SDK (build-tools + a platform) and a JDK. Creates a reusable debug
  keystore on first run. Writes only into -BuildDir; deletes nothing.
.EXAMPLE
  powershell -File build-apk.ps1 -ProjectDir ..\smoke -Out ..\build\smoke.apk
#>

param(
  [Parameter(Mandatory = $true)] [string] $ProjectDir,
  [Parameter(Mandatory = $true)] [string] $Out,
  [string] $BuildDir,
  [string] $SdkRoot   = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
  [string] $Platform  = '',
  [string] $Keystore  = (Join-Path $env:USERPROFILE '.qwenwork\tools\apktool\akanshaa-debug.keystore'),
  [string] $StorePass = 'akanshaa',
  [string] $Alias     = 'akanshaa',
  [int]    $MinApi    = 26,
  [int]    $TargetApi = 34
)

$ErrorActionPreference = 'Continue'

function Fail($msg) { Write-Host "FAILED  $msg" -ForegroundColor Red; exit 1 }
function Ok($msg)   { Write-Host "OK      $msg" -ForegroundColor Green }

function Run($exe, $arguments, $label) {
  Write-Host ">> $label"
  $prev = $ErrorActionPreference
  $ErrorActionPreference = 'SilentlyContinue'
  $items = & $exe @arguments 2>&1
  $code  = $LASTEXITCODE
  $ErrorActionPreference = $prev
  foreach ($i in $items) {
    if ($i -is [System.Management.Automation.ErrorRecord]) {
      $m = "$($i.Exception.Message)".Trim(); if ($m) { Write-Host "   ! $m" }
    } else { Write-Host "   $i" }
  }
  if ($code -ne 0) { Fail "$label exited with code $code" }
  return $items
}

# --- locate tools -----------------------------------------------------------
$ProjectDir = $ProjectDir.TrimEnd('\')
$Manifest = Join-Path $ProjectDir 'manifest\AndroidManifest.xml'
$ResDir   = Join-Path $ProjectDir 'res'
$SrcDir   = Join-Path $ProjectDir 'src'
foreach ($p in @($Manifest, $ResDir, $SrcDir)) { if (-not (Test-Path $p)) { Fail "missing $p" } }

if (-not $BuildDir) { $BuildDir = Join-Path (Split-Path $ProjectDir -Parent) 'build-tools-out' }
$bd = Join-Path $BuildDir (Split-Path $Out -Leaf)
$BuildOut = Join-Path $BuildDir (([IO.Path]::GetFileNameWithoutExtension($Out)))
New-Item -ItemType Directory -Force -Path $BuildOut | Out-Null

$bt = (Get-ChildItem (Join-Path $SdkRoot 'build-tools') -Directory |
  Where-Object { $_.Name -match '^\d+(\.\d+)*$' -and (Test-Path (Join-Path $_.FullName 'zipalign.exe')) } |
  Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName
if (-not $bt) { Fail "no build-tools under $SdkRoot - run the apk-modify setup-toolchain.ps1" }
Ok "build-tools $bt"

if (-not $Platform) {
  $Platform = (Get-ChildItem (Join-Path $SdkRoot 'platforms') -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'android.jar') } |
    Sort-Object {
      $v = ($_.Name -replace '^android-', '') -replace '[^\d.].*$', ''
      if (-not $v) { [version]'0.0' }
      elseif ($v -notmatch '\.') { [version]("$v.0") }
      else { [version]$v }
    } -Descending |
    Select-Object -First 1).Name
  if (-not $Platform) { Fail "no platform with android.jar under $SdkRoot\platforms" }
}
$AndroidJar = Join-Path $SdkRoot "platforms\$Platform\android.jar"
Ok "platform $Platform"

$aapt2 = Join-Path $bt 'aapt2.exe'; $d8 = Join-Path $bt 'd8.bat'
$zipalign = Join-Path $bt 'zipalign.exe'; $apksigner = Join-Path $bt 'apksigner.bat'
foreach ($t in @($aapt2, $d8, $zipalign, $apksigner)) { if (-not (Test-Path $t)) { Fail "tool missing: $t" } }

# --- 1. resources -----------------------------------------------------------
$pkg = (Select-String -Path $Manifest -Pattern 'package="([^"]+)"' | Select-Object -First 1).Matches[0].Groups[1].Value
if (-not $pkg) { Fail 'could not read package= from the manifest' }
Ok "package $pkg"

$resZip = Join-Path $BuildOut 'res.zip'
Run $aapt2 @('compile', '--dir', $ResDir, '-o', $resZip) 'aapt2-compile'

$linked = Join-Path $BuildOut 'linked.apk'
Run $aapt2 @('link', '-o', $linked, '-I', $AndroidJar, '--manifest', $Manifest, $resZip,
             '--min-sdk-version', "$MinApi", '--target-sdk-version', "$TargetApi",
             '--version-code', '1', '--version-name', '0.1-dev') 'aapt2-link'

# --- 2. java ----------------------------------------------------------------
$classes = Join-Path $BuildOut 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = Get-ChildItem $SrcDir -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
if (-not $sources) { Fail "no .java files under $SrcDir" }
Write-Host "   compiling $($sources.Count) source file(s)"
Run 'javac' (@('--release', '11', '-classpath', $AndroidJar, '-d', $classes) + $sources) 'javac'

# --- 3. dex -----------------------------------------------------------------
$classFiles = Get-ChildItem $classes -Recurse -Filter '*.class' | ForEach-Object { $_.FullName }
$dexOut = Join-Path $BuildOut 'dex'
New-Item -ItemType Directory -Force -Path $dexOut | Out-Null
Run $d8 (@("--min-api", "$MinApi", '--lib', $AndroidJar, '--output', $dexOut) + $classFiles) 'd8'
$dex = Join-Path $dexOut 'classes.dex'
if (-not (Test-Path $dex)) { Fail "d8 produced no classes.dex" }

# --- 4. inject dex ----------------------------------------------------------
$withDex = Join-Path $BuildOut 'with-dex.apk'
Copy-Item -LiteralPath $linked -Destination $withDex -Force
Run 'jar' @('uf', $withDex, '-C', $dexOut, 'classes.dex') 'jar-add-dex'

# Prove it actually landed before claiming anything. An APK without classes.dex
# installs and then crashes, so a silent failure here is the worst possible outcome.
$listings = @(& jar 'tf' $withDex 2>$null)
if ($listings -notcontains 'classes.dex') {
  Fail "classes.dex is not inside $withDex - injection failed, refusing to sign an empty app"
}
$dexKb = [int] ((Get-Item $dex).Length / 1KB)
Ok "classes.dex injected (${dexKb} KB)"

# --- 5. align + sign --------------------------------------------------------
$aligned = Join-Path $BuildOut 'aligned.apk'
Run $zipalign @('-f', '-p', '4', $withDex, $aligned) 'zipalign'

if (-not (Test-Path $Keystore)) {
  New-Item -ItemType Directory -Force -Path (Split-Path $Keystore -Parent) | Out-Null
  Run 'keytool' @('-genkeypair', '-keystore', $Keystore, '-storepass', $StorePass, '-keypass', $StorePass,
                  '-alias', $Alias, '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
                  '-dname', "CN=Akanshaa Debug, O=Akanshaa, C=IN") 'create-keystore'
}
Ok "keystore $Keystore"

$outFull = [IO.Path]::GetFullPath($Out)
New-Item -ItemType Directory -Force -Path (Split-Path $outFull -Parent) | Out-Null
Run $apksigner @('sign', '--ks', $Keystore, '--ks-key-alias', $Alias, '--ks-pass', "pass:$StorePass",
                 '--key-pass', "pass:$StorePass", '--min-sdk-version', "$MinApi",
                 '--out', $outFull, $aligned) 'apksigner-sign'
Run $apksigner @('verify', '--print-certs', $outFull) 'apksigner-verify'

# --- 6. report --------------------------------------------------------------
$finalListing = @(& jar 'tf' $outFull 2>$null)
if ($finalListing -notcontains 'classes.dex') {
  Fail "signed APK is missing classes.dex - do not install this"
}
if ($finalListing -notcontains 'AndroidManifest.xml') {
  Fail "signed APK is missing AndroidManifest.xml - do not install this"
}
$dexEntry = ([int]((Get-Item $dex).Length / 1KB))
$size = [math]::Round((Get-Item $outFull).Length / 1KB, 1)
Run $aapt2 @('dump', 'badging', $outFull) 'aapt2-badging' | Out-Null
Write-Host ''
Ok "APK $outFull ($size KB, classes.dex ${dexEntry} KB, entries $($finalListing.Count))"
Write-Host 'CONTENTS CHECK: classes.dex present, AndroidManifest.xml present, signature verified.'
Write-Host 'NOTE: compiled, aligned and signed = VERIFIED. Runtime behaviour is UNVERIFIED until it is installed on a device.'
