@echo off
REM ---------------------------------------------------------------------
REM Anaquel - corre el simulador completo (genera datos y verifica).
REM
REM Sin acentos a proposito: la consola de Windows los rompe segun la
REM pagina de codigos con la que se abra.
REM
REM La contrasena NO vive en este archivo. Se toma de PGPASSWORD si ya
REM esta puesta, y si no, se pregunta aqui y solo dura esta ejecucion.
REM ---------------------------------------------------------------------

setlocal
cd /d "%~dp0"

echo.
echo === Simulador de Anaquel ===
echo.

REM --- Python ----------------------------------------------------------
where python >nul 2>nul
if errorlevel 1 (
    echo ERROR: no encuentro "python" en el PATH.
    echo Instala Python 3 o abre esta ventana desde el entorno donde si este.
    goto :fin
)

REM --- Binarios de PostgreSQL ------------------------------------------
if "%PGBIN%"=="" set "PGBIN=C:\Program Files\PostgreSQL\16\bin"
if not exist "%PGBIN%\psql.exe" (
    echo ERROR: no encuentro psql.exe en:
    echo    %PGBIN%
    echo Si PostgreSQL esta en otra ruta, ponla asi antes de correr esto:
    echo    set PGBIN=D:\ruta\PostgreSQL\16\bin
    goto :fin
)

REM --- Contrasena ------------------------------------------------------
if "%PGPASSWORD%"=="" (
    echo No hay PGPASSWORD en esta ventana.
    set /p PGPASSWORD=Contrasena del usuario postgres:
    echo.
)
if "%PGPASSWORD%"=="" (
    echo ERROR: sin contrasena no se puede abrir la base.
    goto :fin
)

REM --- Correr ----------------------------------------------------------
python verificar.py %*
set CODIGO=%errorlevel%

echo.
if "%CODIGO%"=="0" (
    echo Todo bien. La linea "SIMULADOR OK" de arriba es la que cuenta.
) else (
    echo Algo fallo. Lee la linea que empieza con FALLA: dice que paso se rompio.
)

:fin
echo.
pause
endlocal
