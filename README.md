# Vortex Video Studio

Editor de vídeo nativo para Android (Kotlin, Jetpack Compose, AndroidX Media3). Identidade escura, violeta elétrico e azul-ciano.

> **Estado verificado em 13/10/2026:** o APK debug foi compilado e está em `app/build/outputs/apk/debug/app-debug.apk`; o release também compila, mas sem chave produz APK unsigned. Os 97 testes do `core` passaram e as fontes instrumentadas compilam. A execução instrumentada e a validação do app/exportação em aparelho não foram concluídas nesta sessão.

## Como gerar o APK

Requisitos: Android Studio recente (que suporte AGP 9.3 — "Quail 3" ou superior), JDK 17+ (o JBR do Studio serve), Android SDK Platform 37 e Build-Tools (o Studio oferece baixar), internet para o Gradle baixar as dependências.

```bash
# 1) APK de depuração (instalável direto)
./gradlew :app:assembleDebug
#    saída: app/build/outputs/apk/debug/app-debug.apk

# 2) instalar em aparelho/emulador com depuração USB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3) testes
./gradlew :core:test                    # 97 testes JVM (rodam sem Android)
./gradlew :app:connectedDebugAndroidTest   # instrumentados (aparelho/emulador)

# 4) APK de lançamento
cp keystore.properties.example keystore.properties   # preencha com SUA chave (não versionada)
./gradlew :app:assembleRelease
#    sem keystore.properties o resultado é app-release-unsigned.apk (não instalável)
```

Também é possível abrir a pasta no Android Studio e usar **Build ▸ Build APK(s)**. Se a sincronização reclamar de versões, ajuste **somente** `gradle/libs.versions.toml` (AGP 9.3.0, Kotlin 2.3.21, Gradle 9.5.0, Media3 1.11.1, Compose BOM 2026.09.00, compileSdk 37, minSdk 23).

Em Android Studio mais antigo (AGP 8.x), troque o `agp` para uma versão 8.x, aplique também o plugin `org.jetbrains.kotlin.android` em `app/build.gradle.kts` e use `kotlinOptions`/Gradle 8.x — o AGP 9 trouxe Kotlin embutido.

## Estrutura

- `core/` — Kotlin puro (sem Android): modelo de projeto e linha do tempo multifaixa, operações de edição, desfazer/refazer, JSON versionado, efeitos, keyframes, plano de exportação, modelos, detecção de pausas. **Testado.**
- `app/` — Android: telas Compose, importação, miniaturas, persistência, prévia (ExoPlayer), motor de efeitos (OpenGL), exportação (Media3 Transformer → MP4 H.264/AAC). **Debug e release compilados; execução em aparelho ainda não verificada.**
- `docs/` — relatório final, arquitetura e resultados de testes.

## Privacidade

Tudo é local. Sem anúncios, contas, rastreadores ou envio de mídia. Única permissão: notificações (aviso de fim de exportação).

## GitHub Actions

O workflow `.github/workflows/android.yml`, na raiz Git do repositório, executa os testes do `core`, compila o APK debug, roda os testes instrumentados em um emulador Android e publica os artefatos na execução. Para baixar o APK, abra **Actions**, selecione a execução concluída e baixe `vortex-video-studio-debug`.

O APK release assinado é opcional. Configure os segredos do repositório `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`; a chave é decodificada somente no runner e não é versionada.
