@echo off
REM One-click build for Windows. Needs gradlew.bat + gradle\wrapper\ (see README) and Java 21.
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo Full build failed. Retrying without the in-world overlay...
  call gradlew.bat build -PnoWorldRender
)
echo.
echo If the build succeeded, your mod is in build\libs\  (use the file WITHOUT -sources)
pause
