@echo off
setlocal
set GRADLE_VERSION=8.11.1
set PROJECT_DIR=%~dp0
set DIST_ROOT=%PROJECT_DIR%.gradle-dist
set DIST_DIR=%DIST_ROOT%\gradle-%GRADLE_VERSION%
set ARCHIVE=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip

if exist "%DIST_DIR%\bin\gradle.bat" goto run
if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
if not exist "%ARCHIVE%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ARCHIVE%'"
  if errorlevel 1 exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Expand-Archive -Path '%ARCHIVE%' -DestinationPath '%DIST_ROOT%' -Force"
if errorlevel 1 exit /b 1

:run
call "%DIST_DIR%\bin\gradle.bat" %*
endlocal
