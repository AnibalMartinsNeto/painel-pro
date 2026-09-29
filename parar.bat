@echo off
chcp 65001 >nul
title PainelPro - Parar
cd /d "%~dp0"

echo.
echo  Desligando o QA Panel Pro...

rem Fecha as janelas abertas pelo iniciar.bat (e os processos dentro delas).
taskkill /FI "WINDOWTITLE eq PainelPro - Backend*" /T /F >nul 2>&1 && echo  [ok] Backend desligado
taskkill /FI "WINDOWTITLE eq PainelPro - Front*" /T /F >nul 2>&1 && echo  [ok] Front desligado

rem Garante que as portas ficaram livres (ex.: backend aberto por outro terminal).
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":8080 .*LISTENING"') do taskkill /PID %%p /T /F >nul 2>&1 && echo  [ok] Porta 8080 liberada
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":5173 .*LISTENING"') do taskkill /PID %%p /T /F >nul 2>&1 && echo  [ok] Porta 5173 liberada

rem Para o banco (os dados ficam guardados no volume do Docker).
set "DOCKER=docker"
where docker >nul 2>&1 || set "DOCKER=C:\Program Files\Docker\Docker\resources\bin\docker.exe"
"%DOCKER%" compose -f backend\compose.yaml stop >nul 2>&1 && echo  [ok] Banco PostgreSQL parado (dados preservados)

echo.
echo  Tudo desligado.
timeout /t 5
