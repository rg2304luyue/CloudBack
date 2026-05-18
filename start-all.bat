@echo off
chcp 65001 >nul
title CloudBack + CloudFront 一键启动

echo ============================================
echo  CloudBack + CloudFront 一键启动
echo ============================================
echo.

REM 脚本所在目录即 CloudBack 根目录
set BACKEND=%~dp0
set FRONTEND=%~dp0..\CloudFront

echo 目录:
echo   后端: %BACKEND%
echo   前端: %FRONTEND%
echo.

REM ============================================================
echo [1/3] 编译后端所有模块...
echo ============================================================
cd /d "%BACKEND%"
call mvn clean install -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo ❌ 编译失败，请检查错误
    pause
    exit /b 1
)
echo ✅ 编译通过
echo.

REM ============================================================
echo [2/3] 启动微服务（7 个独立窗口）...
echo ============================================================

REM ---- 第一批：基础服务 ----
start "Gateway :8080"    cmd /c "cd /d "%BACKEND%" && title Gateway :8080    && mvn spring-boot:run -pl cloud-gateway"
echo 已启动: cloud-gateway :8080
timeout /t 3 /nobreak >nul

start "Auth :8081"       cmd /c "cd /d "%BACKEND%" && title Auth :8081       && mvn spring-boot:run -pl cloud-auth"
echo 已启动: cloud-auth :8081
timeout /t 2 /nobreak >nul

start "User :8083"       cmd /c "cd /d "%BACKEND%" && title User :8083       && mvn spring-boot:run -pl cloud-user"
echo 已启动: cloud-user :8083
timeout /t 2 /nobreak >nul

start "Product :8082"    cmd /c "cd /d "%BACKEND%" && title Product :8082    && mvn spring-boot:run -pl cloud-product"
echo 已启动: cloud-product :8082

REM 等第一批启动并注册到 Nacos
echo.
echo 等待第一批服务启动（15秒）...
timeout /t 15 /nobreak >nul

REM ---- 第二批：依赖 Feign 的服务 ----
start "Cart :8084"       cmd /c "cd /d "%BACKEND%" && title Cart :8084       && mvn spring-boot:run -pl cloud-cart"
echo 已启动: cloud-cart :8084
timeout /t 2 /nobreak >nul

start "Order :8085"      cmd /c "cd /d "%BACKEND%" && title Order :8085      && mvn spring-boot:run -pl cloud-order"
echo 已启动: cloud-order :8085
timeout /t 2 /nobreak >nul

start "Payment :8086"    cmd /c "cd /d "%BACKEND%" && title Payment :8086    && mvn spring-boot:run -pl cloud-payment"
echo 已启动: cloud-payment :8086

echo.
echo 等待第二批服务启动（10秒）...
timeout /t 10 /nobreak >nul

REM ============================================================
echo [3/3] 启动前端...
echo ============================================================
cd /d "%FRONTEND%"
start "CloudFront :4173" cmd /c "cd /d "%FRONTEND%" && title CloudFront :4173 && npm run preview"
echo 已启动: CloudFront :4173

echo.
echo ============================================
echo  ✅ 全部启动完成！
echo ============================================
echo.
echo   Nacos 控制台 : http://192.168.91.130:8848/nacos
echo   前端页面     : http://localhost:4173
echo.
echo   关闭各 CMD 窗口即可停止对应服务
echo.
pause
