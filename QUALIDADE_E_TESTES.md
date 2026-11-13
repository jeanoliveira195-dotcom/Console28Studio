# Qualidade sonora e verificação — Console 28 Studio 0.2

## Evidências verificadas neste ambiente

- **PASS:** `kotlinc` + JVM validou a criação de WAV PCM com cabeçalho RIFF e tamanho de dados coerente, usando o WavWriter real do app.
- **PASS:** `clang -fsyntax-only -Wall -Wextra` validou o código C da ponte JNI com os cabeçalhos da JDK.
- **PASS:** `bash -n` analisou sem erros o shell script de compilação nativa.

## Verificações **não realizadas** nesta entrega

- **PENDENTE:** Compilação do Rust e execução de seus testes (falta `rustc`/`cargo`).
- **PENDENTE:** Linkagem cruzada para Android ARM64/x86_64 (falta SDK/NDK).
- **PENDENTE:** Compilação e instalação do APK em aparelho real.
- **PENDENTE:** Medições com sinais de teste processados **pelo binário Rust compilado** e testes de escuta A/B com volume igualado.
- **IMPOSSÍVEL DE CERTIFICAR AQUI:** equivalência à Neve 8028 real, pois não foram fornecidas respostas de frequência, medições de distorção dinâmica, transformadores de referência nem acesso à unidade original.

## O que foi alterado para o caráter analógico

1. Corte inferior de 65 Hz no preset VOZ para reduzir ronco e ruído de manuseio na parte tratada da mistura.
2. Shelf de 135 Hz, EQ de presença em ~2,4 kHz e shelf de brilho em ~8,5 kHz; curvas variam conforme parâmetros.
3. Waveshaper com assimetria moderada para produzir harmônicos pares e ímpares. Integração pela antiderivada (ADAA1) reduz aliasing em comparação com aplicar uma `tanh` bruta amostra a amostra, mas não o elimina.
4. Bloqueio de DC de 8 Hz na via tratada e saturação suave de picos de amostra acima de -0,45 dBFS aprox. (limite assintótico ~-0,13 dBFS). Não garante true peak máximo.
5. Presets priorizam pequenas alterações para gravação de voz e efeitos mais fortes somente no modo VINTAGE.

## Critérios para autorizar gravação no YouTube

- Executar `cargo test --offline --manifest-path rust/Cargo.toml`, sem falhas.
- Compilar `gradle --no-daemon :app:assembleDebug` e confirmar que contém `lib/arm64-v8a/libconsole28_jni.so`.
- Instalar em Android 8+ e confirmar gravação/reprodução de um WAV seco e outro tratado, em 48 kHz quando disponível.
- Conferir cabeçalho, duração e integridade dos dois arquivos (importação em CapCut/DaVinci).
- Ouvir em fones de referência, comparar a volumes iguais, e diminuir drive caso perceba sibilância ou saturação excessiva.
- Medir loudness (LUFS), ruído e true peak em ferramenta externa antes de publicar vídeos.

Uma emulação de uma mesa inteira também depende de pré-amplificadores, fita, acústica e mixagem. Este aplicativo é um **processador estéreo/mono pós-captura**, não um estúdio analógico integral.
