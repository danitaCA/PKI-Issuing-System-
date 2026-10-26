@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Script for PKI Issuing CA System
@REM ----------------------------------------------------------------------------
@echo off
setlocal

if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
    set "PATH=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin;%PATH%"
) else if exist "C:\Program Files\Java\jdk-21.0.12" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
    set "PATH=C:\Program Files\Java\jdk-21.0.12\bin;%PATH%"
)

set "MAVEN_CMD=mvn"
where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 goto run_mvn

if exist "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd" (
    set "MAVEN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd"
    goto run_mvn
)

for /f "delims=" %%I in ('dir /b /s "%USERPROFILE%\.m2\wrapper\dists\*mvn.cmd" 2^>nul') do (
    set "MAVEN_CMD=%%I"
    goto run_mvn
)

echo ERROR: Maven executable not found in PATH or .m2 cache.
exit /b 1

:run_mvn
"%MAVEN_CMD%" %*
exit /b %ERRORLEVEL%
