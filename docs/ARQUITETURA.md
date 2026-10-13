# Arquitetura

```
core (JVM puro, testado)            app (Android, debug/release compilados; runtime não verificado)
├─ model/    projeto, trilhas,      ├─ ui/        Compose: home, editor, linha do tempo,
│            clipes, efeitos,       │             painéis, exportação, configurações
│            keyframes, cor (FxState)├─ data/      ProjectRepository (JSON atômico + .bak),
├─ edit/     Edits (operações       │             AppSettings
│            puras) + ProjectEditor ├─ media/     MediaImporter, Thumbnails, DeviceEncoders,
│            (histórico)            │             AudioAnalyzer, MediaAvailability
├─ json/     JSON + ProjectJson     └─ engine/    PreviewController (ExoPlayer),
├─ export/   ExportPlanner →                      TimelineFxEffect (shader GL único),
│            RenderPlan, FxTimeline,              Overlays/TextRenderer, GainAudioProcessor,
│            EngineCapabilities                   CompositionFactory, ExportManager
├─ templates/ modelos → projetos reais
├─ ai/       SilenceDetector (local)
└─ timeline/ TimelineMath
```

## Decisões principais

1. **Um núcleo puro e testável.** Toda a lógica de edição e de "o que renderizar" vive no `core`. A camada Android só traduz o `RenderPlan` em objetos do Media3 e desenha a interface.
2. **Prévia e exportação compartilham o cálculo de efeitos.** `FxEvaluator`/`FxTimeline` (testados) produzem um `FxState` por instante; o mesmo `TimelineFxEffect` (um único passe GLSL) o aplica no `ExoPlayer.setVideoEffects` (prévia) e na `Composition` do Transformer (exportação).
3. **Efeitos no nível da composição, com tempo absoluto.** Efeitos, mergulhos de transição e keyframes usam o tempo da linha do tempo, evitando depender de como o Transformer reinicia timestamps por item.
4. **Imutabilidade + histórico barato.** `Project` é imutável; cada passo de desfazer guarda só uma referência. Arrastar controles gera uma única entrada (coalescência por chave).
5. **Persistência segura.** JSON com `schemaVersion` e passos de migração; gravação em arquivo temporário + `rename`, com `.bak`; marca de sessão para detectar fechamento inesperado.
6. **O que a interface oferece reflete o motor.** `EngineCapabilities` lista o que é renderizado de verdade (hoje: fade para preto/branco); transições que exigem composição entre clipes aparecem desabilitadas e o planejador avisa se um projeto as contém.
7. **Texto/imagem sobrepostos** são bitmaps com tamanho relativo ao quadro (fração da altura), animados por `OverlayAnimation`; a prévia os desenha em Compose com o mesmo renderizador e a mesma curva de animação.

## Pipeline de exportação

`Project` → `ExportPlanner.plan` (valida mídias, recorta áudio/sobreposições ao fim, calcula `FxTimeline`, raias de áudio) → `OverlayFactory` (bitmaps) → `CompositionFactory` (sequência principal + uma sequência por raia de áudio + efeitos de composição + limite de fps + tone-mapping HDR→SDR) → `Transformer` (H.264 + AAC, taxa de bits por qualidade) → arquivo temporário → MediaStore (`Movies/Vortex Video Studio`, Android 10+) → notificação.
