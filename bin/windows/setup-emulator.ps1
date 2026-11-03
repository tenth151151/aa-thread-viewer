# =============================================================
# setup-emulator.ps1
#   Windows のホストで、Android エミュレータを用意して起動し、
#   コンテナの adb から使えるように adb server を起動するスクリプト。
#   コンテナには KVM (/dev/kvm) がないので、エミュレータはホストの Windows で動かす。
#
# 使い方:
#   bin\windows\setup-emulator.bat                  # 既定の API の AVD を用意して起動する。
#   bin\windows\setup-emulator.bat 37.0 my-avd      # 版と AVD の名前を指定する。
#   bin\windows\setup-emulator.bat /?               # 使い方を表示する (-h と --help も同じ)。
#   .bat は、このファイルを呼ぶだけの入口である。PowerShell から直接実行するときは、
#   ExecutionPolicy で止まることがあるので、次のように指定する。
#   powershell -NoProfile -ExecutionPolicy Bypass -File bin\windows\setup-emulator.ps1
#   PowerShell のセッションから版を渡すときは、引用符で囲む (.\setup-emulator.ps1 '37.0')。
#   囲まないと 37.0 が数値になり、版を正しく読めない。
#
# 引数:
#   1 番目  システムイメージの API の版。既定は、リポジトリのルートの versions.properties の
#           ANDROID_PLATFORM (android-<版>) の版。
#           system-images;android-{版};google_apis;x86_64 を使う。
#   2 番目  AVD の名前。既定は aatv-api-{版}。
#
# 環境変数:
#   ANDROID_HOME  Android SDK の場所。なければ %LOCALAPPDATA%\Android\Sdk を使う。
#
# 流れ:
#   1. SDK と、必要なコマンドを探す。
#   2. システムイメージがなければ入れる (ライセンスに未同意なら、sdkmanager が同意を尋ねる)。
#   3. AVD がなければ作る。
#   4. adb server が、すべての口で待ち受けていなければ、待ち受ける形で起動し直す。
#      待ち受けていれば、起動し直さずにそのまま使う。
#   5. 指定した AVD のエミュレータが動いていなければ、空いているポートで起動する。
#      起動の完了は、そのエミュレータの serial (emulator-<ポート>) を指定して待つ。
#      別の AVD のエミュレータが動いていても、指定した AVD は別に起動する。
#
# ※ すべての口で待ち受けると、同じネットワークの別の機械からも adb を操作できる。
#   詳しくは README の「エミュレータ (Windows)」の節を見る。
# ※ このファイルは BOM 付きの UTF-8 で、改行は CRLF で保存する。
#   Windows PowerShell 5.1 は、BOM がない UTF-8 を既定のコードページで読んで、日本語が化けるため。
#   引数は param() ではなく $args で受ける。-h が PowerShell の引数の名前として解釈されるのを避けるため。
# =============================================================

# 外部コマンドの標準エラーへの出力で止まらないよう、Stop にはしない。失敗は $LASTEXITCODE で確かめる。
$ErrorActionPreference = 'Continue'

function Write-Info([string]$Message) {
    Write-Host "[setup-emulator] $Message"
}

function Write-Fail([string]$Message) {
    [Console]::Error.WriteLine("[setup-emulator] $Message")
}

function Show-Usage {
    Write-Host '使い方: setup-emulator.bat [API の版 [AVD の名前]]'
    Write-Host '  API の版   既定は、versions.properties の ANDROID_PLATFORM (android-<版>) の版です。system-images;android-<版>;google_apis;x86_64 を使います。'
    Write-Host '  AVD の名前 既定は aatv-api-<版> です。'
    Write-Host 'PowerShell のセッションから版を渡すときは、引用符で囲んでください (例: .\setup-emulator.ps1 ''37.0'')。'
}

# versions.properties の ANDROID_PLATFORM (android-<版>) から、版を読む。読めないときは null を返す。
function Get-DefaultApi {
    $root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $file = Join-Path $root 'versions.properties'
    if (-not (Test-Path -LiteralPath $file)) {
        Write-Fail "versions.properties が見つかりません: $file"
        return $null
    }
    foreach ($line in (Get-Content -LiteralPath $file -Encoding UTF8)) {
        if ($line -match '^ANDROID_PLATFORM=android-(\d+(\.\d+)*)\s*$') { return $Matches[1] }
    }
    Write-Fail 'versions.properties に ANDROID_PLATFORM=android-<版> の行が見つかりません。'
    return $null
}

# ポート 5037 を、すべての口 (IPv4 の 0.0.0.0 か、IPv6 の ::) で adb が待ち受けているかを調べる。
# Windows 10.0.26300、adb 37.0.1 では、adb -a start-server のあとの 0.0.0.0:5037 の行は、
# Get-NetTCPConnection の State が空で (Listen にならない)、netstat -ano にも出なかった。
# そのため、State では絞らず、持ち主のプロセスの名前が adb であることで判断する。netstat は使わない。
function Test-Listening {
    $connections = Get-NetTCPConnection -LocalPort 5037 -ErrorAction SilentlyContinue
    foreach ($connection in $connections) {
        if ($connection.LocalAddress -ne '0.0.0.0' -and $connection.LocalAddress -ne '::') { continue }
        $process = Get-Process -Id $connection.OwningProcess -ErrorAction SilentlyContinue
        if ($process -and $process.Name -eq 'adb') {
            return $true
        }
    }
    return $false
}

# PowerShell のセッションから 37.0 と書くと、小数になって 37 に変わる。元の文字列は分からないので、推測せずに止める。
# 整数は、文字列にしても情報を失わないので、文字列にして使う。
# 数値と文字列の -eq は変換で失敗することがあるため、文字列にする処理は、ほかの引数の比較より前に行う。
# Windows PowerShell 5.1 には [short] などの型の短い名前の一部がないため、.NET の完全な型名で判定する。
$arg0 = $null
if ($args.Count -ge 1 -and $null -ne $args[0]) {
    $first = $args[0]
    if ($first -is [System.String] -or $first -is [System.Int32] -or $first -is [System.Int64] -or $first -is [System.Int16] -or
        $first -is [System.Byte] -or $first -is [System.SByte] -or $first -is [System.UInt16] -or $first -is [System.UInt32] -or
        $first -is [System.UInt64]) {
        $arg0 = [string]$first
    } else {
        Write-Fail "API の版が文字列でも整数でもなく、$($first.GetType().Name) として渡されました。版を正しく読めません。"
        Write-Fail "引用符で囲んで渡してください (例: .\setup-emulator.ps1 '37.0')。または、setup-emulator.bat から実行してください。"
        exit 1
    }
}

if ($arg0 -and ($arg0 -eq '/?' -or $arg0 -eq '-h' -or $arg0 -eq '--help')) {
    Show-Usage
    exit 0
}

if ($arg0) {
    $api = $arg0
} else {
    $api = Get-DefaultApi
    if (-not $api) { exit 1 }
}
$avd = "aatv-api-$api"
if ($args.Count -ge 2 -and $args[1]) { $avd = [string]$args[1] }

# -------------------------------------------------------------
# SDK とコマンドを探す
# -------------------------------------------------------------
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
$avdmanager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$adb = Join-Path $sdk 'platform-tools\adb.exe'

if (-not (Test-Path -LiteralPath $sdk)) {
    Write-Fail "Android SDK のフォルダが見つかりません: $sdk"
    Write-Fail 'Android Studio で SDK を入れるか、環境変数 ANDROID_HOME に SDK の場所を指定してください。'
    exit 1
}

$missing = $false
if (-not (Test-Path -LiteralPath $sdkmanager)) {
    Write-Fail 'sdkmanager が見つかりません。SDK Manager の SDK Tools で、Android SDK Command-line Tools (latest) を入れてください。'
    $missing = $true
}
if (-not (Test-Path -LiteralPath $avdmanager)) {
    Write-Fail 'avdmanager が見つかりません。SDK Manager の SDK Tools で、Android SDK Command-line Tools (latest) を入れてください。'
    $missing = $true
}
if (-not (Test-Path -LiteralPath $emulator)) {
    Write-Fail 'emulator が見つかりません。SDK Manager の SDK Tools で、Android Emulator を入れてください。'
    $missing = $true
}
if (-not (Test-Path -LiteralPath $adb)) {
    Write-Fail 'adb が見つかりません。SDK Manager の SDK Tools で、Android SDK Platform-Tools を入れてください。'
    $missing = $true
}
if ($missing) { exit 1 }

# sdkmanager と avdmanager は Java が要る。PATH と JAVA_HOME になければ、Android Studio 同梱の JDK を使う。
if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not $env:JAVA_HOME) {
    $jbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath (Join-Path $jbr 'bin\java.exe')) { $env:JAVA_HOME = $jbr }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not $env:JAVA_HOME) {
    Write-Fail 'Java が見つかりません。環境変数 JAVA_HOME を指定するか、Android Studio を標準の場所に入れてください。'
    exit 1
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

# -------------------------------------------------------------
# システムイメージ
# -------------------------------------------------------------
$pkg = "system-images;android-$api;google_apis;x86_64"
if (Test-Path -LiteralPath (Join-Path $sdk "system-images\android-$api\google_apis\x86_64\source.properties")) {
    Write-Info "システムイメージは入っています: $pkg"
} else {
    Write-Info "システムイメージを入れます: $pkg"
    # $pkg は空白を含まず、引用符なしだと cmd.exe のバッチが ; で引数を分けるため、引用符ごと渡す。
    & $sdkmanager "--sdk_root=$sdk" --install "`"$pkg`""
    if ($LASTEXITCODE -ne 0) {
        Write-Fail 'システムイメージを入れられませんでした。API の版を確かめてください。'
        exit 1
    }
}

# -------------------------------------------------------------
# AVD
# -------------------------------------------------------------
$avdList = & $avdmanager list avd -c 2>$null
# 大文字小文字を区別して比べる。
if ($avdList -ccontains $avd) {
    Write-Info "AVD はあります: $avd"
} else {
    Write-Info "AVD を作ります: $avd"
    # カスタムのハードウェアプロファイルを作るかを尋ねられるので、no と答える。
    # $pkg は、上と同じ理由で引用符ごと渡す。
    'no' | & $avdmanager create avd -n $avd -k "`"$pkg`""
    if ($LASTEXITCODE -ne 0) {
        Write-Fail 'AVD を作れませんでした。'
        exit 1
    }
}

# -------------------------------------------------------------
# adb server
#   -a で、localhost だけでなく、すべての口で待ち受ける。コンテナから届くようにするため。
#   すでにすべての口で待ち受けているときは、起動し直さずにそのまま使う。起動し直すと、
#   起動中のエミュレータが adb devices に出るまでに時間がかかることがある (すぐに出る保証はない)。
# -------------------------------------------------------------
if (Test-Listening) {
    Write-Info 'adb server はすでにすべての口で待ち受けているので、そのまま使います。'
} else {
    Write-Info 'adb server を、すべての口で待ち受ける形で起動し直します。'
    # server が動いていないと、kill-server が接続失敗のメッセージを標準エラーに出すので捨てる。
    & $adb kill-server 2>$null
    & $adb -a start-server
    if (-not (Test-Listening)) {
        # start-server で -a が効かなかったときは、前面で動く server を別のウィンドウで起動する。
        # 上と同じ理由で、標準エラーを捨てる。
        & $adb kill-server 2>$null
        Start-Process -FilePath 'cmd.exe' -WindowStyle Hidden -ArgumentList "/c start `"adb server`" /min `"$adb`" -a nodaemon server"
        $listened = $false
        for ($i = 0; $i -lt 15; $i++) {
            if (Test-Listening) { $listened = $true; break }
            Start-Sleep -Seconds 1
        }
        if (-not $listened) {
            Write-Fail 'adb server が、すべての口で待ち受けていません。ポート 5037 を使っている別のプロセスがないか確かめてください。'
            exit 1
        }
        Write-Info 'adb server は、最小化したウィンドウ (adb server) で動いています。そのウィンドウを閉じると、adb server が止まります。'
    }
}
Write-Info 'adb server は、すべての口 (ポート 5037) で待ち受けています。'
Write-Info '初めてのときは、Windows のファイアウォールの確認が出ます。許可する範囲は README を見て決めてください。'

# -------------------------------------------------------------
# エミュレータの起動
# -------------------------------------------------------------
# エミュレータは、AVD の名前で特定し、その serial (emulator-<ポート>) を指定して待つ。
# エミュレータが複数動いていると、adb -e は使えないため。

# adb devices に出ているエミュレータの serial を、状態を問わずに返す。
function Get-EmulatorSerials {
    $serials = @()
    foreach ($line in @(& $adb devices 2>$null)) {
        if ("$line" -match '^(emulator-\d+)\s+\S+') { $serials += $Matches[1] }
    }
    return $serials
}

# qemu-system-x86_64 のプロセスの数を返す。
# emulator.exe は起動元で、エミュレータの 1 インスタンスにつき、子の qemu-system-x86_64 が 1 つ動く。
function Get-EmulatorProcessCount {
    return @(Get-Process -Name 'qemu-system-x86_64' -ErrorAction SilentlyContinue).Count
}

# 指定した AVD のエミュレータの serial を返す。なければ null を返す。
# adb -s <serial> emu avd name は、1 行目に AVD の名前を出す。
# offline などで答えない serial があると、その数を $script:NoAnswerCount に入れる。
function Find-AvdSerial([string]$AvdName) {
    $script:NoAnswerCount = 0
    foreach ($serial in @(Get-EmulatorSerials)) {
        $lines = @(& $adb -s $serial emu avd name 2>$null)
        $name = $lines | Where-Object { "$_".Trim() } | Select-Object -First 1
        if (-not $name) { $script:NoAnswerCount++; continue }
        if ("$name".Trim() -ceq $AvdName) { return $serial }
    }
    return $null
}

# 同じ AVD を二重に起動しないよう、見つからないときは、次の間は待ってから探し直す。
# - qemu-system-x86_64 のプロセスの数が、adb devices のエミュレータの serial の数より多い間。
#   adb server を起動し直した直後は、動いているエミュレータが adb devices に 1 台ずつ出るため。最長で約 30 秒待つ。
# - AVD の名前に答えない serial がある間。最長で約 10 秒待つ。
# 待っても解消しないときは、起動せずに止める。
$avdSerial = $null
$processWaits = 0
$noAnswerWaits = 0
while ($true) {
    $avdSerial = Find-AvdSerial $avd
    if ($avdSerial) { break }
    if ($script:NoAnswerCount -gt 0) {
        if ($noAnswerWaits -ge 5) {
            Write-Fail 'adb devices のエミュレータに、AVD の名前に答えないものがあります (起動中か、offline の可能性があります)。'
            Write-Fail '同じ AVD を二重に起動するおそれがあるので、起動しません。エミュレータの起動が終わってから、またはエミュレータを終了してから、もう一度実行してください。'
            exit 1
        }
        if ($noAnswerWaits -eq 0) { Write-Info 'AVD の名前に答えないエミュレータがあります。答えるのを待ちます。' }
        $noAnswerWaits++
    } elseif ((Get-EmulatorProcessCount) -gt @(Get-EmulatorSerials).Count) {
        if ($processWaits -ge 15) {
            Write-Fail 'qemu-system-x86_64 のプロセスの数が、約 30 秒待っても、adb devices のエミュレータの数に合いません。'
            Write-Fail '同じ AVD を二重に起動するおそれがあるので、起動しません。エミュレータを終了するか、プロセスを止めてから、もう一度実行してください。'
            exit 1
        }
        if ($processWaits -eq 0) { Write-Info 'adb devices に出ていないエミュレータのプロセスがあります。出るのを待ちます。' }
        $processWaits++
    } else {
        break
    }
    Start-Sleep -Seconds 2
}

if ($avdSerial) {
    Write-Info "AVD のエミュレータはすでに動いています: $avd ($avdSerial)。起動し直さずに使います。"
} else {
    # 5554 から 5584 までの偶数のうち、adb devices のエミュレータが使っていないポートを選ぶ。
    $usedPorts = @(Get-EmulatorSerials | ForEach-Object { [int]($_ -replace '^emulator-', '') })
    $port = $null
    foreach ($candidate in 5554, 5556, 5558, 5560, 5562, 5564, 5566, 5568, 5570, 5572, 5574, 5576, 5578, 5580, 5582, 5584) {
        if ($usedPorts -notcontains $candidate) { $port = $candidate; break }
    }
    if (-not $port) {
        Write-Fail 'エミュレータ用のポート (5554 から 5584 までの偶数) がすべて使われています。動いているエミュレータを終了してください。'
        exit 1
    }
    $avdSerial = "emulator-$port"
    Write-Info "エミュレータを起動します: $avd ($avdSerial)"
    Start-Process -FilePath $emulator -ArgumentList '-avd', "`"$avd`"", '-port', "$port"
}

Write-Info '起動の完了を待ちます。最長で約 8 分かかります。'
$booted = $false
for ($i = 0; $i -lt 90; $i++) {
    $prop = & $adb -s $avdSerial shell getprop sys.boot_completed 2>$null
    # 出力が複数行でも、どれかの行が 1 で始まれば完了とみなす。
    if (@($prop | Where-Object { "$_".StartsWith('1') }).Count -gt 0) { $booted = $true; break }
    Start-Sleep -Seconds 5
}
if (-not $booted) {
    Write-Fail "起動の完了を待ちきれませんでした ($avdSerial)。エミュレータのウィンドウを確かめてください。"
    exit 1
}

Write-Info "準備ができました。使うエミュレータは $avdSerial です。接続中の端末は次のとおりです。"
& $adb devices
Write-Info 'コンテナで ADB_SERVER_SOCKET を有効にしてあれば、コンテナの adb devices にも出ます。'
Write-Info 'コンテナでは、./gradlew assembleDebug のあとに adb install -r app/build/outputs/apk/debug/app-debug.apk でアプリを入れます。installDebug は使えません。'
exit 0
