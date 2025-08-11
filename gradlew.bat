@echo off
setlocal

set DIRNAME=%~dp0

"%DIRNAME%gradle\wrapper\gradle-wrapper.jar" %*
