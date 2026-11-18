@echo off
title Clase12 - EurekaClient Addition (Puerto 8010)
cd /d "%~dp0"
echo ====================================================
echo  Iniciando Clase12_EurekaClient_Addition en puerto 8010
echo  Asegurese de que EurekaServer (8761) este corriendo.
echo ====================================================
mvnw.cmd spring-boot:run
pause
