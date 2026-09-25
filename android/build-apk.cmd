@echo off
REM ============================================================
REM  一键构建 APK（Windows）
REM  用法：双击本文件，或在项目根目录执行  android\build-apk.cmd
REM  产物：android\app\build\outputs\apk\release\app-release.apk
REM ============================================================
setlocal

REM ---- 按需修改这两个路径 ----
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Java\jdk-21"
if "%ANDROID_HOME%"=="" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"

cd /d "%~dp0"

REM 写 local.properties（Gradle 靠它找 SDK）
echo sdk.dir=%ANDROID_HOME:\=\\%> local.properties

echo [1/2] JAVA_HOME   = %JAVA_HOME%
echo [2/2] ANDROID_HOME= %ANDROID_HOME%
echo.

call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
    echo.
    echo 构建失败，请检查上面的报错。
    exit /b 1
)

echo.
echo 构建成功，产物：
echo   %~dp0app\build\outputs\apk\release\app-release.apk
endlocal
