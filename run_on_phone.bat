@echo off
chcp 65001 >nul
REM ============================================================
REM  一键构建 + 安装 + 启动到手机
REM  用法：双击运行，或在命令行执行 run_on_phone.bat
REM ============================================================

set "JAVA_HOME=D:\software\Android\Android Studio\jbr"
set "ADB=D:\software\adb\adb.exe"
cd /d D:\learning\bike_navi

echo [1/4] 检查设备连接...
%ADB% devices | findstr "device$" >nul
if errorlevel 1 (
    echo  错误：未检测到已连接的设备。请确认手机已开启 USB 调试并连接。
    pause
    exit /b 1
)
echo  设备已连接。

echo [2/4] 构建 Debug APK...
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
    echo  构建失败，请查看上方错误信息。
    pause
    exit /b 1
)

echo [3/4] 安装到手机...
%ADB% install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo  安装失败。
    pause
    exit /b 1
)

echo [4/4] 授予定位权限并启动应用...
%ADB% shell pm grant com.example.bikenavi android.permission.ACCESS_FINE_LOCATION 2>nul
%ADB% shell pm grant com.example.bikenavi android.permission.ACCESS_COARSE_LOCATION 2>nul
%ADB% shell am start -n com.example.bikenavi/.MainActivity

echo.
echo  完成！应用已在手机上启动。
pause
