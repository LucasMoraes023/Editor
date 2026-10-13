# Relatório final — Vortex Video Studio

Data: 13/10/2026. Este relatório inclui um registro inicial e uma atualização com builds executados nesta sessão (seção 10).

## 1. Resumo

Projeto Android Studio completo (Kotlin + Jetpack Compose + AndroidX Media3) de um editor de vídeo com linha do tempo multifaixa, cortes, efeitos GPU, textos, áudio e exportação MP4 H.264/AAC. A lógica de edição e de planejamento da exportação está em um módulo Kotlin puro **compilado e testado aqui (97 testes aprovados)**. A camada Android compilou em debug e release nesta sessão; a inicialização do app e os fluxos de edição/exportação ainda não foram validados em aparelho.

**APK debug gerado:** `app/build/outputs/apk/debug/app-debug.apk` (assinatura debug verificada). O release foi gerado sem assinatura própria e não é distribuível. A compilação não comprova que o app ou a exportação funcionem em runtime.

## 2. Resultado da compilação inicial — registro anterior à seção 10

Os resultados abaixo descrevem o estado informado antes dos builds desta sessão. A seção 10 contém as verificações atuais.

| Verificação | Resultado |
|---|---|
| Ferramentas no ambiente | só JRE 21; sem `javac`, Gradle, Android SDK, kotlinc (instalei kotlinc 2.3.21 e JUnit 4.13.2 para o núcleo) |
| Rede | `dl.google.com`, `maven.google.com`, `repo.maven.apache.org`, `services.gradle.org`, `plugins.gradle.org` **bloqueados** → impossível baixar AGP, AndroidX, Compose, Media3 → **impossível compilar o app** |
| Núcleo (`core`) | compilado com kotlinc 2.3.21 (JVM 17): **sucesso** |
| Camada Android (`app`) | **não compilada**. Verificações estáticas feitas: sintaxe (parser do kotlinc não acusou erro de sintaxe); 37 imports do Media3 conferidos contra o código-fonte do Media3 1.11.1; ~35 assinaturas de API do Media3 conferidas no código-fonte; 246 strings referenciadas = 246 definidas; recursos referenciados existem |
| APK de depuração | **não gerado** |
| APK de lançamento | **não gerado** (e exigiria sua chave; nenhuma credencial foi inventada ou embutida) |

Espere **alguns erros de compilação** ao abrir a camada Android pela primeira vez: ~3,9 mil linhas de Kotlin/Compose foram escritas sem compilador. Os pontos mais prováveis estão listados na seção 8.

## 3. Funcionalidades

Legenda: ✅ lógica verificada por testes JVM · 🟡 app compilado, **runtime não verificado** · ⛔ não implementado

**Projeto e linha do tempo**
- ✅ Modelo multifaixa (vídeo, imagem, texto, música, efeitos sonoros, narração), posição/duração/origem/velocidade/volume/efeitos por item
- ✅ Cortar início/fim, dividir, remover, reordenar, duplicar, ajustar entrada/saída (também por deslocamento), velocidade 0,1×–8×, volume 0–200%, mudo, fades, rotação 90°, espelho, zoom/posição, proporção do projeto, fotos na sequência (duração configurável)
- ✅ Desfazer/refazer (200 passos, coalescência de sliders), falhas não alteram o estado
- ✅ Salvamento em JSON versionado com migração; leitura tolerante a campos/tipos desconhecidos; versões futuras rejeitadas com mensagem
- 🟡 Interface da linha do tempo: cabeçote central, pinça/botões de zoom, régua adaptável, miniaturas, forma de onda real, seleção, alças de corte, arrastar para mover/reordenar com ímãs
- ✅ Modelos: 5 formatos em branco (Reels, TikTok, Shorts, YouTube, quadrado) + 7 modelos com textos posicionados (redes sociais, motivacional, cinematográfico, comercial) que geram projetos reais

**Efeitos (20)** — preto e branco, sépia, vintage, cinema, contraste, saturação, temperatura, brilho, nitidez, desfoque, vinheta, granulação, zoom, rotação, espelho H/V, efeito RGB, glitch, aberração cromática, brilho de luz
- ✅ Cálculo do estado (matrizes de cor, intensidades, composição de efeitos)
- 🟡 Execução na GPU: um único shader GLSL (`TimelineFxEffect`) usado na prévia e na exportação

**Transições**
- ✅/🟡 **Fade para preto e fade para branco** (mergulho): planejadas e testadas; render não verificado
- ⛔ Dissolver, deslizar (4 direções), zoom: exigem compor dois clipes ao mesmo tempo. A interface mostra essas opções **desabilitadas** e o planejador avisa se um projeto as contém (não há renderização falsa)

**Texto e sobreposições**
- 🟡 Texto com fonte (6 famílias do sistema), tamanho, cor, negrito/itálico, alinhamento, contorno, sombra, fundo, posição (arrastar na prévia), duração (alças), 8 animações de entrada/saída, vários textos; imagem/logotipo com tamanho, opacidade, rotação
- ✅ Curvas de animação e plano de sobreposição (recorte ao fim do vídeo)

**Áudio**
- ✅ Planejamento: música/efeito/narração em faixas livres, posição, recorte, volume, fades, raias sem sobreposição, corte no fim do projeto
- 🟡 Importação de arquivos de áudio, pré-escuta sincronizada, forma de onda, processador de ganho/fades, mixagem multi-sequência com lacunas, exportação com áudio

**Velocidade e animação**
- ✅ Velocidade constante (modelo/plano); keyframes de escala/posição/rotação (avaliação, divisão de clipes) e preset "Zoom suave"
- ⛔ Curvas de velocidade; editor manual de keyframes; opacidade de clipe de vídeo

**Exportação**
- ✅ Resoluções 720p/1080p/4K, 30/60 fps, 3 qualidades; tamanho de saída par; taxa de bits; estimativa de tamanho; opções habilitadas conforme o codificador; regra de espaço (20% de folga ×2)
- 🟡 Transformer H.264+AAC, progresso real, cancelamento, limpeza de temporários, MediaStore (Android 10+; antes disso, pasta do app), notificação, abrir/compartilhar, marca de exportação interrompida, mensagens de erro por causa, tela acesa durante a exportação, tone-mapping HDR→SDR

**Desempenho e robustez**
- 🟡 Prévia com qualidade adaptável (360/540/720) e efeitos desligáveis; miniaturas com cache LRU e no máximo 2 decodificações simultâneas; sem carregar vídeos na memória; tratamento de `OutOfMemoryError` na exportação; autosave (600 ms) + salvamento ao pausar/sair; recuperação após fechamento inesperado; detecção de mídia movida/excluída + relocalização
- ⛔ Foreground service para exportar com o app em segundo plano (a exportação roda no processo do app; se o sistema o encerrar, o app avisa na próxima abertura)

**IA (local)**
- ✅ Detecção de pausas (algoritmo RMS) e remoção em um passo de desfazer
- 🟡 Decodificação do áudio (MediaCodec) que alimenta a detecção e a forma de onda
- ⛔ Legendas automáticas (exigem modelo de reconhecimento de voz), remoção de fundo, sugestões de enquadramento, títulos por IA, recomendação de formatos — **nada foi simulado**; a arquitetura local (`core/ai`) está pronta para recebê-los

**Outros**
- 🟡 Configurações (qualidade/efeitos da prévia, padrões de exportação, cache), política de privacidade, sobre, explicação de operações locais x externas; strings em `strings.xml` (pt-BR; pronto para tradução). Sem anúncios, sem contas, única permissão: notificações
- ⛔ Segunda língua, recursos premium/cobrança (propositalmente ausentes)

## 4. Tecnologias

Kotlin 2.3.21 · Jetpack Compose (BOM 2026.09.00) + Material 3 · AndroidX Media3 1.11.1 (ExoPlayer, Transformer, Effect) · OpenGL ES 2.0 (GLSL) · Coroutines · Photo Picker / Storage Access Framework · MediaStore · Gradle 9.5.0 / AGP 9.3.0 · JUnit 4.

Versões escolhidas por consulta às fontes oficiais em 13/10/2026 (lançamentos/tags do Media3, Gradle, Kotlin; documentação do AGP e do Compose BOM). Todos os módulos Media3 usam a mesma versão.

## 5. Versão mínima do Android

minSdk 23 (Android 6.0); targetSdk 36; compileSdk 37. Limites conhecidos por versão: salvamento na galeria via MediaStore só no Android 10+; notificação de conclusão exige permissão no Android 13+.

## 6. Resultado dos testes

Ver `docs/TESTES.md`. Resumo: **97 testes JVM do núcleo, 97 aprovados, 0 reprovados**. Testes instrumentados (abertura, persistência em disco, exportação real, cancelamento, arquivos inválidos) **foram escritos, mas não puderam ser executados** (sem aparelho/emulador/SDK). Nenhum vídeo exportado foi inspecionado, porque nenhuma exportação ocorreu.

## 7. Caminho do APK

Nenhum APK existe. Ao compilar: `app/build/outputs/apk/debug/app-debug.apk`. Instruções de instalação e compilação em `README.md`.

## 8. Limitações conhecidas e suposições a validar em aparelho

Tudo abaixo é código escrito com base na leitura do código-fonte do Media3 1.11.1, mas **nunca executado**:

1. **Runtime:** o app compila com as versões em `gradle/libs.versions.toml`; inicialização, compatibilidade por dispositivo e fluxos completos ainda precisam ser validados em aparelhos.
2. **Espaço de cor do shader:** o pipeline SDR do Media3 entrega cores em gama; o shader converte (≈2,2) para aplicar as matrizes de cor (definidas em RGB linear). Conferir visualmente brilho/contraste.
3. **Sobreposições:** o Media3 desenha o bitmap em pixels relativos ao quadro (lido em `OverlayMatrixProvider`); posição/tamanho de textos exportados deve ser comparada com a prévia.
4. **Tempo dos efeitos:** efeitos usam tempo absoluto da linha do tempo no nível da composição; confirmar que mergulhos de transição e "Zoom suave" caem nos instantes certos.
5. **Prévia:** `ExoPlayer.setVideoEffects` com o efeito "ao vivo"; troca de lista de efeitos ao mudar a rotação de clipe pode causar um soluço; fades de áudio não aparecem na prévia, volume limitado a 100%, textos desenhados pelo Compose (mesmo renderizador e animação, mas não idêntico pixel a pixel).
6. **Áudio na exportação:** velocidade altera o tom (API de preservação de tom é experimental e não foi usada); mixagem de várias sequências com lacunas e `experimentalSetForceAudioTrack` precisa de teste com arquivos reais.
7. **60 fps:** só aumenta a fluidez se a origem tiver 60 fps (o app não cria quadros).
8. **Mídia persistente:** o app tenta `takePersistableUriPermission` nas URIs; provedores que não permitem fazem a mídia ficar indisponível após reiniciar o aparelho — o app detecta e oferece relocalizar, mas não copia mídia para dentro do projeto.
9. **Exportação em segundo plano** não usa serviço em primeiro plano.
10. **Transições** e **faixas de vídeo extras**: só existem no modelo/UI de seleção (desabilitadas), sem renderização.
11. **Release:** R8 e lint vital passaram nesta sessão; o APK gerado é unsigned e não foi instalado/executado.
12. **Teste instrumentado de exportação** assume codificador H.264 em 720p no aparelho de teste.

## 9. Próximos passos recomendados

1. Instalar o APK debug em um aparelho/emulador compatível e confirmar inicialização, importação, edição e persistência.
2. Rodar os testes instrumentados e exportar um vídeo real com texto, filtro, fade e música; inspecionar o MP4 (duração, resolução, sincronia).
3. Validar as suposições 2–6 acima com mídia real em 2–3 aparelhos (básico, intermediário, avançado).
4. Etapa 5: composição de duas sequências de vídeo (dissolver/deslizar/zoom, faixas extras, PIP) com teste de renderização antes de habilitar na interface.
5. Etapa 6: legendas automáticas com reconhecimento de voz local (modelo embarcado, com consentimento explícito para download), remoção de fundo, foreground service para exportação, preservação de tom, editor de keyframes.

## 10. Atualização da auditoria — 13/10/2026

O projeto foi extraído do arquivo `VortexVideoStudio.zip` e depois movido para a raiz do repositório. A execução real de `./gradlew :core:test` com o wrapper Gradle 9.5.0 e JDK 25 foi aprovada, com 97 testes. A primeira tentativa revelou um erro de compilação do script `app/build.gradle.kts`: o nome `java` era interpretado como extensão do DSL do Gradle; um import explícito de `java.util.Properties` corrigiu o problema.

Depois de instalar o Android SDK Platform 37.2 e Build-Tools 37.0 no diretório do usuário, `:app:assembleDebug` passou. O APK de 23.501.440 bytes está em `app/build/outputs/apk/debug/app-debug.apk`, package `app.vortex.studio.debug`, `minSdk 23`, `targetSdk 36`; `apksigner` verificou as assinaturas v1/v2. Os primeiros erros Kotlin foram corrigidos: o filtro do Photo Picker usa `ImageAndVideo` e os rótulos localizados de efeitos são materializados no contexto Compose. O recurso estático `audio_hint` também foi marcado `formatted="false"` para tratar percentuais literais corretamente.

`:app:compileDebugAndroidTestKotlin` passou. `:app:connectedDebugAndroidTest` terminou com `No connected devices`: o emulador API 35 foi iniciado sem aceleração KVM, mas encerrou durante a execução instrumentada. Nenhum teste instrumentado chegou a gerar resultados; o app não foi instalado/aberto no emulador e a exportação final não foi inspecionada.

`:app:assembleRelease` passou com `--max-workers=1`, incluindo R8 e lint vital; o artefato é `app/build/outputs/apk/release/app-release-unsigned.apk` porque nenhuma chave foi configurada. A tentativa inicial com paralelismo padrão perdeu o daemon durante R8; a execução serial concluiu.

As dependências Android foram resolvidas com Media3 1.11.1 em todos os módulos e Compose BOM 2026.09.00 (Compose UI 1.12.1 e Material3 1.4.0). O workflow `.github/workflows/android.yml` está na raiz Git e foi atualizado para a estrutura achatada; ainda não foi executado no GitHub. É necessário enviar os arquivos da raiz e o workflow para publicar o artefato remoto.
