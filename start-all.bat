@echo off
pushd %~dp0
title CloudBack Start

echo ============================================
echo  CloudBack + CloudFront
echo ============================================
echo.
echo WORK DIR: %CD%
echo.

echo [1/3] mvn clean install -DskipTests ...
echo.
call mvn clean install -DskipTests
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)
echo OK
echo.

echo [2/3] Starting services ...
echo.

start "Gateway" /D . cmd /k "mvn spring-boot:run -pl cloud-gateway"
timeout 3 >nul
start "Auth"    /D . cmd /k "mvn spring-boot:run -pl cloud-auth"
timeout 2 >nul
start "User"    /D . cmd /k "mvn spring-boot:run -pl cloud-user"
timeout 2 >nul
start "Product" /D . cmd /k "mvn spring-boot:run -pl cloud-product"
echo Waiting 15s ...
timeout 15 >nul

start "Cart"    /D . cmd /k "mvn spring-boot:run -pl cloud-cart"
timeout 2 >nul
start "Order"   /D . cmd /k "mvn spring-boot:run -pl cloud-order"
timeout 2 >nul
start "Payment" /D . cmd /k "mvn spring-boot:run -pl cloud-payment"
echo Waiting 10s ...
timeout 10 >nul

echo.
echo [3/3] Starting frontend ...
pushd ..\CloudFront
start "CloudFront" /D . cmd /k "npm run preview"
popd

echo.
echo All done!
echo   Nacos: http://192.168.91.130:8848/nacos
echo   Front: http://localhost:4173
echo.
pause
