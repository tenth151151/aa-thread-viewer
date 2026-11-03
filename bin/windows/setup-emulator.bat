@echo off
rem Entry point only. The real work is in setup-emulator.ps1 (same folder).
rem Keep this file ASCII only: cmd.exe misreads non-ASCII lines.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-emulator.ps1" %*
exit /b %ERRORLEVEL%
