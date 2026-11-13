# Como gerar o APK — Console 28 Studio

O arquivo `app-debug.apk` só existe **depois** que uma compilação real concluir com êxito. Nenhum `.apk` foi produzido no ambiente de preparação deste projeto por não haver Android SDK, NDK, Gradle ou Rust instalados.

## Mais simples: GitHub Actions (sem instalar SDK no PC)

1. Crie um repositório **novo** (preferencialmente privado) chamado `Console28Studio` no seu GitHub.
2. Extraia o ZIP e envie **todo o conteúdo de `Console28Studio`** para a raiz do repositório, inclusive `.github/workflows/android-apk.yml`. Não suba apenas o ZIP.
3. Na aba **Actions**, selecione **Compilar APK Android** e execute **Run workflow**. Também inicia automaticamente ao enviar para o ramo `main` ou `master`, se Actions estiver habilitado.
4. Aguarde a conclusão do job e confirme que o status ficou **verde**.
5. Abra a execução e baixe o artefato `console28-android-debug-apk`. Extraia e instale o arquivo `app-debug.apk` no Android.

## Localmente no Linux

Instale Java 17 ou superior (JDK 17 recomendado), Gradle 8.13, Rust com rustup e Android SDK com `platforms;android-36`, `build-tools;35.0.0`, `ndk;27.2.12479018`. Configure `ANDROID_HOME` para o caminho do SDK.

```bash
cd Console28Studio
bash scripts/compilar-apk-linux.sh
```

Saída esperada **somente após compilação bem-sucedida**:

```
app/build/outputs/apk/debug/app-debug.apk
```

A compilação depende de conexão com Maven/Google na primeira execução. O APK de depuração é próprio para teste e não é uma distribuição assinada de produção. Os testes automatizados não substituem avaliação do gravador e do motor DSP num aparelho físico.
