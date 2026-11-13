# Console 28 Studio — v0.5.0

## Interface hi-fi analógica, ícone e indicadores em tempo real

- Visual inspirado na mesa de áudio aprovada: superfícies pretas foscas, contornos âmbar, indicadores verdes e vermelhos, pouco ruído visual em tela vertical.
- Novo ícone adaptativo para o launcher do Android.
- Espectro de 16 bandas e VU L/R derivados do áudio PCM realmente reproduzido pelo aplicativo, depois do DSP em Rust (estimativas estéticas, não medições certificadas).
- Knobs manipuláveis por arraste vertical na aba Mixagem; sliders tradicionais preservados na aba Estúdio.
- O filtro Rust, a compressão dos graves, as músicas locais, favoritos, playlists e a gravação WAV permanecem.
- Testes locais e testes unitários Android do monitor; APK de desenvolvimento compilado no GitHub Actions.
- Limitações: sem reprodução como serviço em segundo plano; o espectro cobre apenas o sinal PCM processado no app.


## Novidades v0.4 — graves com peso e compressão controlada

- **Preset Música reajustado:** low-shelf de graves mais audível, compressão abaixo de aproximadamente 155 Hz, saturação reduzida e saída com margem de segurança.
- **Novo slider:** `Compressão dos graves`, separado de `Peso dos graves`. O compressor usa detector com ataque/release suavizados e redução de ganho vinculada entre canais para preservar o balanço estéreo.
- **Limitador PCM em Rust:** limita picos das amostras a aproximadamente -0,45 dBFS, sem alegação de limitação true-peak ou eliminação total de distorção.
- **Mixagem mais acessível:** aba dedicada com sliders sempre visíveis e atalho na Biblioteca.
- **Validação:** testes do núcleo Rust, verificação sintática JNI e build Android debug passaram no GitHub Actions. Ainda falta escuta e medição em dispositivos reais.

> Os filtros são inspirados em aparelhos analógicos clássicos, não são uma reprodução calibrada/licenciada da Neve 8028.


## Novidades da versão 0.3

- **Biblioteca automática:** reconhece arquivos MP3, FLAC e outros áudios locais indexados pelo Android (MediaStore) após autorização. Para Android 13+, usa READ_MEDIA_AUDIO; nos anteriores, READ_EXTERNAL_STORAGE.
- **Playlists offline:** Todas, Adicionadas, Favoritas e playlists personalizadas, armazenadas localmente no aparelho. Inclusão e remoção de músicas sem modificar arquivos originais.
- **Busca** por título, artista e álbum; fila de reprodução com próxima/anterior.
- **Interface hi-fi vintage:** visual inspirado nos aparelhos japoneses de CD da década de 1980, com grafite, latão envelhecido e display âmbar; navegação Biblioteca / Playlists / Estúdio e miniplayer.
- **Estúdio de voz:** mantém gravação original + processada em WAV, com DSP Rust nativo.
- **Limitações atuais:** músicas protegidas por DRM/armazenadas dentro de Spotify ou Apple Music não podem ser importadas; arquivos não indexados pelo Android podem ser abertos manualmente, mas não passam a integrar a biblioteca. Ainda não há serviço de reprodução em segundo plano, capa de álbum real nem sincronização de playlists. Esta é uma versão debug de testes.


Aplicativo Android offline para reprodução de MP3/FLAC e gravação de narração WAV, com processamento DSP inspirado em consoles britânicos analógicos clássicos. Interface Kotlin/Jetpack Compose; núcleo de áudio em Rust + JNI.

> A emulação não reproduz comprovadamente a Neve 8028 original. O APK deve ser compilado e testado em um dispositivo Android.

## Como gerar o APK

1. Extraia o pacote `console28_studio_rust_projeto_compilacao.zip` disponibilizado na conversa do ChatGPT.
2. Entre na pasta `Console28Studio` extraída.
3. Envie **o conteúdo dessa pasta** para a raiz deste repositório, preservando as pastas `app`, `rust`, `native`, `scripts` e os arquivos `.gradle.kts`.
4. Já configurei `.github/workflows/android-apk.yml` neste repositório. Se aparecer conflito, mantenha esta versão.
5. Abra a aba **Actions**, selecione **Compilar APK Android (Rust + Kotlin)** e use **Run workflow**.
6. Após o build passar, baixe o artifact **console28-android-debug-apk** (conterá `app-debug.apk`).

Esse é um APK de desenvolvimento não assinado para publicação. Para distribuição, gere um APK de release assinado.

## Avisos

- A captura de microfone depende de permissão Android.
- O aplicativo exporta WAV 16-bit adequado para edição posterior de vídeo.
- A fidelidade da simulação Neve deve ser avaliada por testes auditivos e instrumentos de áudio.
