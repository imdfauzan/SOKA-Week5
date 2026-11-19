@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

rem ---------------------------------------------------------------
rem  Jalankan halaman simulasi + CloudSim Plus asli (Java) di laptop.
rem  Server menyala di jendela terpisah; tutup jendela itu untuk Stop.
rem ---------------------------------------------------------------

if not defined JAVA_HOME (
  if exist "C:\Program Files\Java\jdk-17.0.19\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17.0.19"
  )
)
if not defined JAVA_HOME (
  echo [!] JAVA_HOME belum diset dan JDK 17 tidak ditemukan di lokasi default.
  echo     Set dulu, contoh:  set JAVA_HOME=C:\Program Files\Java\jdk-17.0.19
  pause
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "MVN=mvn"
where mvn >nul 2>nul
if errorlevel 1 (
  if exist "%LOCALAPPDATA%\apache-maven-3.9.16\bin\mvn.cmd" (
    set "MVN=%LOCALAPPDATA%\apache-maven-3.9.16\bin\mvn.cmd"
  ) else (
    echo [!] Maven tidak ditemukan di PATH maupun di %LOCALAPPDATA%\apache-maven-3.9.16
    echo     Build manual: mvn clean package
    pause
    exit /b 1
  )
)

where node >nul 2>nul
if errorlevel 1 (
  echo [!] Node.js tidak ditemukan di PATH. Server lokal butuh Node.js.
  pause
  exit /b 1
)

if not exist "target\soka-simulasi.jar" (
  echo [1/3] Build jar CloudSim Plus ^(sekali saja, butuh internet^)...
  call "%MVN%" -q -B clean package
  if errorlevel 1 (
    echo [!] Build gagal.
    pause
    exit /b 1
  )
) else (
  echo [1/3] Jar sudah ada, build dilewati.
)

echo [2/3] Menyalakan server lokal di jendela baru...
start "CloudSim Server - tutup jendela ini untuk stop" cmd /k node "%~dp0tools\cloudsim-server.js"

echo [3/3] Membuka browser...
timeout /t 2 /nobreak >nul
start "" http://127.0.0.1:8777/

echo.
echo Selesai. Halaman: http://127.0.0.1:8777/
echo Untuk menghentikan server, tutup jendela "CloudSim Server".
timeout /t 4 /nobreak >nul
endlocal
