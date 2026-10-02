# Gamepad Layout — Fase 2 (mini console Android)

Kotlin · Jetpack Compose · Material 3 · MVVM. APK gerado pelo GitHub Actions (aba Actions > Build APK).

## Envio (Termux)
```
cd ~ && unzip -o /sdcard/Download/GamepadLayout.zip && cd GamepadLayout
bash enviar-github.sh
```
Se o repositório já tiver histórico: `git push -u origin main --force`.

## Módulos (app/src/main/java/com/gamepadlayout/app)
| Pasta | Função |
|---|---|
| console | Modo Console (tela cheia, horizontal, tela acesa só quando útil) |
| controller | Detecção de controles (InputManager), bateria (Android 12+), tela de mapeamento |
| input | Botões -> ações configuráveis (PadAction), analógicos, gatilhos |
| display | Tela externa: DisplayManager + Presentation (Home em modo TV) |
| cast | Transmissão: MediaProjection + serviço em primeiro plano + servidor MJPEG |
| audio | Saídas de áudio disponíveis + seletor do sistema |
| compat | Verificações de compatibilidade do aparelho |
| ui/... | Home, Jogos, Navegador, Configurações, Tela externa, Menu rápido, Modo Jogo |

## O que o Android permite (e o que não)
- Espelhar a tela para Chromecast/Miracast: só pelo sistema (Cast/Smart View). O app abre essa tela; não há API pública para iniciar o espelhamento por conta própria.
- Home em modo TV: quando há uma tela externa (cabo HDMI ou espelhamento do sistema), o app desenha a Home nela via Presentation.
- Transmitir por Wi-Fi: MediaProjection (com a permissão oficial) + servidor local; a TV/PC abre o endereço no navegador. Só vídeo, sem áudio.
- Áudio: apps não escolhem a saída; o app lista as saídas e abre o seletor do sistema.
- Voltar ao app ao fechar um jogo: o Android decide; o app não força isso.
- Bateria do controle: Android 12+ e só se o controle informar.
