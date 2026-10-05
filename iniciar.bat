@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title PainelPro - Iniciar
cd /d "%~dp0"

echo.
echo  ==========================================
echo   QA Panel Pro - iniciando
echo  ==========================================
echo.

rem ---------- 1. Pré-requisitos ----------
where java >nul 2>&1 || (echo  [ERRO] Java não encontrado. Instale o Java 21: winget install EclipseAdoptium.Temurin.21.JDK & goto :falha)
where node >nul 2>&1 || (echo  [ERRO] Node.js não encontrado. Instale em https://nodejs.org & goto :falha)
set "DOCKER=docker"
where docker >nul 2>&1 || set "DOCKER=C:\Program Files\Docker\Docker\resources\bin\docker.exe"
if not "%DOCKER%"=="docker" if not exist "%DOCKER%" (echo  [ERRO] Docker Desktop não encontrado. Instale: winget install Docker.DockerDesktop & goto :falha)
echo  [ok] Java, Node e Docker instalados

rem ---------- 2. Docker Desktop (o banco roda nele) ----------
rem Obs.: rótulos de goto não podem ficar dentro de blocos if ( ... ) no .bat.
"%DOCKER%" info >nul 2>&1
if not errorlevel 1 goto :dockerOk
echo  [..] Abrindo o Docker Desktop, aguarde...
start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
set /a tentativas=0
:esperaDocker
timeout /t 3 /nobreak >nul
set /a tentativas+=1
"%DOCKER%" info >nul 2>&1
if not errorlevel 1 goto :dockerOk
if !tentativas! geq 60 (echo  [ERRO] O Docker não subiu em 3 minutos. Abra o Docker Desktop manualmente e rode de novo. & goto :falha)
goto :esperaDocker
:dockerOk
echo  [ok] Docker rodando

rem O banco precisa subir AQUI: o jar empacotado não traz o suporte
rem automático a docker compose (ele só existe no modo desenvolvimento).
rem --wait espera o healthcheck do Postgres antes de seguir.
echo  [..] Subindo o banco (PostgreSQL)...
"%DOCKER%" compose -f "%~dp0backend\compose.yaml" up -d --wait >nul 2>&1
if errorlevel 1 (echo  [ERRO] O banco não subiu. Veja: docker compose -f backend\compose.yaml logs & goto :falha)
echo  [ok] Banco rodando

rem ---------- 3. Dependências do front (só na primeira vez) ----------
if exist "frontend\node_modules" goto :depsOk
echo  [..] Primeira execução: instalando dependências do front (1-2 min)...
pushd frontend
call npm install --no-audit --no-fund
popd
:depsOk
echo  [ok] Dependências do front

rem ---------- 4. Backend (porta 8080) ----------
netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul
if errorlevel 1 goto :empacotar
echo  [!] A porta 8080 já está em uso: o backend já está rodando.
echo      Se for uma versão antiga, feche aquela janela (ou use parar.bat) e rode este arquivo de novo.
goto :front

:empacotar
rem Roda o backend EMPACOTADO (java -jar), que sobe em ~8s, em vez de
rem "mvnw spring-boot:run" (~15s, recompila a cada vez). O jar só é
rem refeito quando algum arquivo do código é mais novo que a MARCA
rem target\.empacotado, gravada após cada empacotamento com sucesso (a data
rem do próprio jar não serve: o Spring Boot preserva a data antiga dele).
powershell -NoProfile -Command "$m='backend\target\.empacotado'; if (-not (Test-Path 'backend\target\painel-backend.jar') -or -not (Test-Path $m)) { exit 1 }; $t=(Get-Item $m).LastWriteTime; $n=(Get-ChildItem 'backend\src','backend\pom.xml' -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime; if ($n -gt $t) { exit 1 } else { exit 0 }"
if not errorlevel 1 goto :subirBackend
echo  [..] Código do backend mudou (ou primeira vez): empacotando (~15s)...
pushd backend
call .\mvnw.cmd -q -DskipTests package
if errorlevel 1 (popd & echo  [ERRO] Falha ao compilar o backend. Rode ".\mvnw.cmd package" na pasta backend para ver o erro. & goto :falha)
echo ok> target\.empacotado
popd
:subirBackend
echo  [..] Subindo o backend (Spring Boot + PostgreSQL)...
rem -XX:TieredStopAtLevel=1: compilação JIT simplificada, sobe mais rápido (bom para desenvolvimento).
start "PainelPro - Backend" cmd /k "cd /d "%~dp0backend" && java -XX:TieredStopAtLevel=1 -jar target\painel-backend.jar"

:front
rem ---------- 5. Front (porta 5173) ----------
netstat -ano | findstr /R /C:":5173 .*LISTENING" >nul
if errorlevel 1 goto :subirFront
echo  [!] A porta 5173 já está em uso: o front já está rodando.
goto :esperar
:subirFront
echo  [..] Subindo o front (React + Vite)...
start "PainelPro - Front" cmd /k "cd /d "%~dp0frontend" && npm run dev"

:esperar
rem ---------- 6. Espera a API responder e abre o navegador ----------
echo  [..] Aguardando a API ficar pronta...
set /a tentativas=0
:esperaApi
timeout /t 1 /nobreak >nul
set /a tentativas+=1
curl -s -f -o nul http://localhost:8080/actuator/health
if not errorlevel 1 goto :apiOk
if !tentativas! geq 180 (echo  [ERRO] A API não respondeu em 3 minutos. Veja a janela "PainelPro - Backend". & goto :falha)
goto :esperaApi
:apiOk
echo  [ok] API no ar: http://localhost:8080

start "" http://localhost:5173
echo.
echo  ==========================================
echo   Pronto!  Painel: http://localhost:5173
echo  ==========================================
echo   Backend e front rodam nas janelas "PainelPro - Backend" e "PainelPro - Front".
echo   Para desligar tudo, use parar.bat
echo.
timeout /t 10
exit /b 0

:falha
echo.
pause
exit /b 1
