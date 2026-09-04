# AGP wireless-adb test fix

Local workaround for an AGP bug that makes `connectedAndroidTest` fail on any device whose adb
serial contains a colon — every device connected with `adb connect host:port`.

## The bug

JUnit Platform's `UniqueIdFormat` percent-encodes `:` inside unique-id segment values, so a device
that adb calls `192.168.0.7:5555` becomes `192.168.0.7%3A5555` everywhere the device id is read
back out of a unique id. Two consumers then fail to recognise it:

| where | what it does | symptom |
| --- | --- | --- |
| `DeviceTrackingListener` in `com.android.tools.utp:gradle-work-action` | keys `perDeviceAllTestsPassed` by the encoded id, while `AndroidTestEngineRunner` looks it up by the raw serial in `serials.all { perDeviceAllTestsPassed[it] ?: false }` | task writes exit code 1; Gradle says "There were failing tests" while the HTML report says 100% successful |
| `AndroidTestResultListener` in `com.android.tools.androidtest:android-test-engine-result-listener` | writes the encoded id into the streamed result protos | Android Studio can't match results to a device, renders nothing, dumps raw `<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>` blocks to the build console |

Both patches decode the id at the single point where it is extracted. Tests themselves were never
failing — UTP's own `test-result.pb` records `PASSED` throughout.

Unaffected serials: USB, `emulator-NNNN`, and wireless-debugging mDNS pairings
(`adb-<id>-<suffix>._adb-tls-connect._tcp`). Pairing over mDNS instead of `adb connect` avoids the
bug entirely and needs no patching — that is the better fix if you don't mind re-pairing.

## Rebuilding after an AGP upgrade

Both patches live in jars inside `~/.gradle/caches` and are lost whenever AGP's version changes or
the cache is refreshed. Run these from this directory. They resolve jar paths by wildcard, so they
work across versions as long as the classes still look the same — `PatchListener` fails loudly if
`getDeviceId` is gone, which is the signal that upstream may have fixed it.

### 1. Gradle-side fix (exit code)

```powershell
$cp = ((Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Include junit-platform-launcher-*.jar,junit-platform-engine-*.jar,junit-platform-commons-*.jar,apiguardian-api-*.jar,opentest4j-*.jar -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch "sources" } | Select-Object -First 5 -ExpandProperty FullName) -join ";")
& "$env:JAVA_HOME\bin\javac.exe" -d build -cp $cp DeviceTrackingListener.java
$j = (Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter "gradle-work-action-*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch "sources" } | Select-Object -First 1).FullName
Copy-Item $j "$j.orig" -Force
Push-Location build; & "$env:JAVA_HOME\bin\jar.exe" --update --file $j "com/android/tools/utp/gradle/DeviceTrackingListener.class"; Pop-Location
```

### 2. Studio-side fix (streamed events)

```powershell
$asm = (Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter "asm-9*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch "sources|asm-util|asm-commons|asm-tree|asm-analysis" } | Select-Object -First 1).FullName
& "$env:JAVA_HOME\bin\javac.exe" -d build -cp $asm PatchListener.java
$k = (Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter "android-test-engine-result-listener-*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch "sources" } | Select-Object -First 1).FullName
& "$env:JAVA_HOME\bin\java.exe" -cp "$asm;build" PatchListener $k out
Copy-Item $k "$k.orig" -Force
Push-Location out; & "$env:JAVA_HOME\bin\jar.exe" --update --file $k "com/android/tools/androidtest/listener/AndroidTestResultListener.class"; Pop-Location
```

### 3. Restart daemons

```powershell
Push-Location ..\..; .\gradlew.bat --stop; Pop-Location
```

## Reverting

Do this before diagnosing any real test failure, so you are not debugging a modified toolchain.

```powershell
Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter "*.jar.orig" -ErrorAction SilentlyContinue | ForEach-Object { Copy-Item $_.FullName ($_.FullName -replace '\.orig$','') -Force; Write-Output "reverted $($_.FullName)" }
```

## Verifying

```powershell
Push-Location ..\..; $env:ANDROID_SERIAL = "192.168.0.7:5555"; .\gradlew.bat :app:connectedDebugAndroidTest; Remove-Item Env:ANDROID_SERIAL; Pop-Location
```

Expect `BUILD SUCCESSFUL` and `0` in
`app/build/outputs/androidTest-results/connected/debug/test-result-exit-code.txt`.

To check the Studio-side fix without opening Studio, add
`-Pcom.android.tools.utp.GradleAndroidProjectResolverExtension.enable=true`; the base64
`<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>` blocks it prints should decode to a device id of
`192.168.0.7:5555`, not `192.168.0.7%3A5555`.
