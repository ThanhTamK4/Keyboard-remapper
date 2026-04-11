@echo off
if not exist "build\key-remapper.jar" (
    echo Building first...
    powershell -ExecutionPolicy Bypass -File build.ps1
    if %ERRORLEVEL% NEQ 0 (
        echo Build failed.
        pause
        exit /b 1
    )
)
:: Ollama config (edit these if needed)
if not defined OLLAMA_MODEL set OLLAMA_MODEL=gemma3
if not defined OLLAMA_HOST set OLLAMA_HOST=http://localhost:11434

echo Starting Key Remapper... (model: %OLLAMA_MODEL%)
java --enable-native-access=ALL-UNNAMED -jar build\key-remapper.jar
