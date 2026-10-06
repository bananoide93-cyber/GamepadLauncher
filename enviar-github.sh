#!/data/data/com.termux/files/usr/bin/bash
# Uso: bash enviar-github.sh   (já aponta para o repositório GamepadLauncher)
set -e
URL="${1:-https://github.com/bananoide93-cyber/GamepadLauncher.git}"
git init -q 2>/dev/null || true
git branch -M main
rm -f app/src/main/java/com/gamepadlayout/app/games/AdvAssets.kt 2>/dev/null; printf "package com.gamepadlayout.app.games\n" > app/src/main/java/com/gamepadlayout/app/games/AdvAssets.kt
git add -A
git commit -m "Gamepad Layout - fase 2" || true
git remote remove origin 2>/dev/null || true
git remote add origin "$URL"
git push -u origin main
