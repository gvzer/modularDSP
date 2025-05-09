@echo off
setlocal enabledelayedexpansion

:: 1) Find Ableton Live under Program Files (64-bit first)
set "FOUND="
for %%P in ("%ProgramFiles%" "%ProgramFiles(x86)%") do (
  for /D %%L in ("%%~P\Ableton\Live *") do (
    if exist "%%L\Resources\Max\C74\packages\max-mxj\java-classes\lib\max.jar" (
      set "FOUND=%%L"
      goto :FOUND_LIVE
    )
  )
)
:FOUND_LIVE
if not defined FOUND (
  echo ❌  Couldn’t locate any Ableton Live install in Program Files.
  pause
  exit /b 1
)
echo Using Ableton: %FOUND%

:: 2) Derive Max Java paths
set "MAX_BASE=%FOUND%\Resources\Max\C74\packages\max-mxj\java-classes"
set "DEST=%MAX_BASE%\classes"
set "CP=%MAX_BASE%\lib\max.jar"
if not exist "%DEST%" mkdir "%DEST%"

:: 3) Compile ALL .java in this script’s dir
pushd "%~dp0"
echo Compiling → "%DEST%"
for %%f in (*.java) do (
  echo   ↳ %%~nxf
  javac -d "%DEST%" -cp "%CP%" "%%f"
)
popd

echo.
echo ✅  Done! Classes installed to:
echo %DEST%
pause
