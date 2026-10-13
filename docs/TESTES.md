# Testes — resultados reais e lacunas

Executados em 13/10/2026 no ambiente de desenvolvimento (Linux, JRE 21, kotlinc 2.3.21, JUnit 4.13.2). Log completo: `docs/test_run_core.log`.

## Aprovados — 97 testes JVM do módulo `core`

| Classe | Testes | Cobre |
|---|---:|---|
| `EditsTest` | 26 | adicionar mídia, dividir (inclui velocidade), cortar início/fim (por posição e por deslocamento), remover com fechamento de lacuna, reordenar, duplicar, velocidade, fotos, volume/fades, rotação, efeitos, texto/áudio/sobreposição, keyframes ao dividir, relocalizar mídia, transição, remoção de pausas |
| `ProjectEditorTest` | 8 | desfazer/refazer, "sem mudança" não cria histórico, falha preserva estado, coalescência de sliders (e janela de tempo), limite de histórico |
| `JsonCodecTest` | 4 | ida e volta, Unicode/escapes, inteiros grandes, **documentos inválidos** (11 casos + aninhamento excessivo) |
| `ProjectJsonTest` | 5 | projeto completo ida e volta, esquema futuro rejeitado, conteúdo desconhecido tolerado/inválido rejeitado, migrações, mídias referenciadas |
| `ColorMathTest` | 8 | identidade, saturação/cinza, interpolação, exposição, temperatura, composição de matrizes, layout mat4 |
| `FxEvaluatorTest` | 8 | catálogo (20 efeitos), neutros bipolares, zoom/espelhos, efeitos desligados, rotação |
| `FxTimelineTest` | 4 | mergulhos de transição, fora da linha do tempo, keyframes ("Zoom suave") |
| `KeyframesAndAnimationTest` | 6 | interpolação/HOLD, easing, fade/slide/zoom/pop de entrada e saída |
| `ExportMathTest` | 4 | tamanhos pares por proporção/resolução, taxa de bits, estimativa e regra de armazenamento, opções por capacidade do aparelho |
| `ExportPlannerTest` | 13 | projeto vazio, **mídia ausente**, segmentos, áudio/mudo, raias e corte no fim, sobreposições, transições suportadas x não suportadas, faixas extras, efeitos no plano |
| `TemplatesTest` | 3 | todos os 12 modelos geram projetos válidos, efeitos padrão aplicados |
| `TimelineMathTest` | 4 | conversões, régua, ímãs, limites de zoom |
| `SilenceDetectorTest` | 4 | pausas, bordas, sensibilidade, forma de onda |

Um defeito real foi encontrado pelos testes durante o desenvolvimento e corrigido: o JSON escrevia inteiros acima de 1e15 em notação científica (valores ainda válidos, mas fora do formato esperado).

## Escritos, **não executados** (precisam de aparelho/emulador)

- `AppLaunchTest` — abertura do app e criação de projeto até o editor
- `ProjectRepositoryTest` — salvar/restaurar, recuperação pelo `.bak`, duplicar/excluir, sessão interrompida, arquivo de projeto inválido
- `ExportInstrumentedTest` — exportação real (MP4 com duração e resolução inspecionadas), projeto vazio, mídia ausente, cancelamento

## Checklist dos testes obrigatórios do enunciado

| Item | Situação |
|---|---|
| abertura do aplicativo | escrito (`AppLaunchTest`), **não executado** |
| criação de projeto | lógica aprovada (`TemplatesTest`); fluxo de UI **não executado** |
| importação de vídeo | **sem teste** (depende de aparelho e arquivos reais) |
| reprodução | **sem teste** |
| corte / divisão | **aprovado** (lógica); efeito na prévia/exportação **não verificado** |
| rotação / proporção | **aprovado** (modelo e tamanhos); renderização **não verificada** |
| aplicação de filtro | **aprovado** (cálculo do estado); GPU **não verificada** |
| inserção de texto | **aprovado** (modelo/plano/animação); render **não verificado** |
| adição de áudio | **aprovado** (modelo/plano/raias); mixagem **não verificada** |
| salvamento e recuperação | **aprovado** em JVM (JSON); em disco real **não executado** |
| exportação | escrito (`ExportInstrumentedTest`), **não executado**; vídeo final **não inspecionado** |
| cancelamento de exportação | escrito, **não executado** |
| arquivos inválidos | **aprovado** (JSON, projeto, mídia ausente no planejador); demais **não executados** |
| armazenamento insuficiente | regra **aprovada** (`ExportMathTest`); fluxo real **não testado** |
| tratamento de falhas | **aprovado** no núcleo (`EditOutcome.Failed`, `PlanResult.Failed`); camada Android **não testada** |

## Verificações estáticas da camada Android (não substituem compilar)

- Parser do kotlinc sobre todas as fontes: nenhum erro de sintaxe (os demais erros eram só referências a bibliotecas ausentes do classpath).
- 37 imports `androidx.media3.*` existem no código-fonte do Media3 1.11.1.
- Cada API do Media3 usada (Transformer, Composition, EditedMediaItem(Sequence), Effects, OverlaySettings, BitmapOverlay, BaseGlShaderProgram, GlProgram, BaseAudioProcessor, ExoPlayer.setVideoEffects, PlayerView) foi localizada no código-fonte.
- 246 chaves `R.string.*` usadas = 246 definidas em `strings.xml`; recursos `R.drawable`/`mipmap`/`xml` referenciados existem.

## Verificação nesta sessão — 13/10/2026

- Ambiente: wrapper Gradle 9.5.0 executado com JDK 25; Android SDK Platform 37.2 e Build-Tools 37 instalados no diretório do usuário. O emulador API 35 foi iniciado em software, mas não permaneceu conectado durante a tarefa instrumentada.
- `./gradlew :core:test`: **aprovado**, 97 testes JUnit.
- `./gradlew :app:dependencies --configuration debugCompileClasspath`: **aprovado**; Media3 1.11.1 (todos os módulos alinhados) e Compose BOM 2026.09.00 foram resolvidos. O BOM selecionou Compose UI 1.12.1 e Material3 1.4.0.
- `./gradlew :app:assembleDebug`: **aprovado**. APK em `app/build/outputs/apk/debug/app-debug.apk`; assinatura debug v1/v2 verificada.
- `./gradlew :app:compileDebugAndroidTestKotlin`: **aprovado**; fontes instrumentadas compilam.
- `./gradlew :app:connectedDebugAndroidTest`: **não executado**: o Gradle terminou com `No connected devices`. O emulador x86_64 sem KVM não permaneceu disponível durante a execução; nenhum teste instrumentado produziu resultado.
- `./gradlew :app:assembleRelease`: **aprovado** com `--max-workers=1`; R8 e lint vital passaram. Foi gerado `app/build/outputs/apk/release/app-release-unsigned.apk`, não instalável até assinatura própria.
- O APK não foi iniciado em aparelho/emulador e a exportação real não foi inspecionada.
- O workflow `.github/workflows/android.yml` foi validado sintaticamente como YAML e configura build/testes/publicação; não foi executado no GitHub, pois os fontes extraídos e o workflow ainda precisam ser enviados ao repositório.
