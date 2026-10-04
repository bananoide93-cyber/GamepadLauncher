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

## Novidades da v4
- Tela sempre horizontal (fixa no AndroidManifest). O Modo Console agora só controla tela cheia e tela acesa.
- Teclado operado por controle no navegador (endereço e campos de texto das páginas).
- Foco inicial corrigido: o controle já seleciona o primeiro item de cada tela.
- Jogos classificados: com suporte a controle, emuladores, precisam de mapeador (beta), sem classificação.
- Botão Home do Android volta para a Home do app quando ele é a tela inicial.

## Novidades da v5
- Home da TV agora tem o MESMO layout da Home do celular (barra de status, categorias, jogos e dicas), só ampliado.
  Navegação na TV: D-pad (esquerda/direita/cima/baixo), A confirma. Linha de cima = categorias; linha de baixo = jogos.
- Novo módulo **Arcade** com 5 jogos nativos (pasta `games/`): Dungeon 3D (estilo Doom, raycasting), Nave Espacial,
  Galinha na Estrada, Cobrinha e Tijolinhos. Controle ou toque. START pausa. Recordes salvos no aparelho.
- A lógica dos jogos não depende do Android (interface `Gfx`), por isso foi compilada e testada por simulação.

## v6
- Novo ícone minimalista (roxo chapado).
- Home em blocos quadrados (estilo console, original), com faixa colorida e detalhe por categoria; vale também para a tela da TV.
- Jogos do Arcade com visual retrô: sprites em pixel art, fonte pixelada, efeito de monitor antigo (CRT).
- Novos módulos: Arquivos, Downloads, Wi-Fi, Bluetooth e Sistema (bateria, RAM, armazenamento, galeria, música).
- TV com menos atraso: jogos do Arcade aparecem na tela externa por cabo (sem codificar vídeo); transmissão Wi-Fi com predefinição "Latência mínima", quadros adiados em vez de descartados e buffer de rede menor.

## v7
- Foco com controle: as telas internas (Jogos, Arquivos...) focam o primeiro item sozinhas; mexer no controle também foca (sem tocar no botão).
- Abrir ao desbloquear (Configurações): exige permissão "exibir sobre outros apps", confirmação dupla, espera, intervalo mínimo, só com controle, só carregando, pausa (1 h/8 h), notificação com "Pausar" e "Desativar", e "Sair do app" no menu rápido (START).
- Papel de parede: Roxo clássico, Synthwave, Céu estrelado ou foto da galeria, com ajuste de escurecimento.
- Arcade: novos jogos "Estrada Neon" (corrida pseudo-3D) e "Corredor Veloz" (plataforma de velocidade).

## v8
- **Modo só-controle**: ao detectar um controle, o toque na tela é bloqueado; ele volta após 10 s sem usar o controle. Também há proteção de foco na Home (os botões passam a reagir ao controle sem precisar apertar X antes).
- **Arcade com 9 jogos**: novos "Arena de Sobrevivência" (ondas, upgrades ao subir de nível, loja com moedas, 4 classes, chefe a cada 5 ondas) e "Caçadores de Monstros" (5 áreas, 54 monstros, 5 raridades, Dex, captura com cristais, progresso salvo).
- **Nave**: chefe a cada 5 níveis e escolha de 1 upgrade (de 3) após cada chefe.
