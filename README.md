# Console 28 Studio

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
