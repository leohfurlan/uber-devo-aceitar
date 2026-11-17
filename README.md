# Devo aceitar?

Protótipo Android do recomendador de corridas e entregas. A tela **Simulador de ofertas** reproduz cinco cenários e mostra a análise acima do cartão da oferta simulada. Em **Preferências**, escolha a fonte de energia e informe preço e consumo na unidade correspondente. O app divide o preço pelo rendimento e soma outros custos por km, quando informados. O custo direto por km continua disponível, assim como os ajustes de metas, nota de alerta e bairro a evitar.

## Build local

Use JDK 17 e Android SDK 35. Configure `ANDROID_HOME` para o SDK ou indique o caminho com `sdk.dir` em `local.properties`. Na raiz do projeto, execute `gradlew.bat :app:assembleDebug` no Windows ou `./gradlew :app:assembleDebug` em outro sistema. O APK de depuração fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Ensaio com dois apps no Android

1. Compile com `gradlew.bat :app:assembleDebug :lab:assembleDebug` no Windows ou `./gradlew :app:assembleDebug :lab:assembleDebug` em outro sistema. O segundo APK fica em `lab/build/outputs/apk/debug/lab-debug.apk`.
2. Instale ambos os APKs. Em **Configurações → Acessibilidade**, ative **Devo Aceitar? · modo laboratório**.
3. Abra **Laboratório de ofertas** e escolha um cenário. A análise deve aparecer acima da oferta com uma pequena folga. Verifique se ela acompanha a troca de oferta e some ao aceitar, recusar, expirar ou sair do laboratório.

O serviço de depuração identifica a janela do laboratório, lê os campos disponíveis da oferta na árvore de acessibilidade e calcula os resultados no aparelho. A janela da análise não recebe toques. O serviço está disponível apenas no build de depuração.

Para testar preferências e análises simuladas em um único app, compile `gradlew.bat :app:assembleSimulator`. O APK em `app/build/outputs/apk/simulator/app-simulator.apk` é identificado como **Devo aceitar? Simulador** e inclui somente a análise dentro do próprio app, sem serviço de acessibilidade.

A regra de bairro compara o nome do destino com uma preferência local. A espera da coleta vem do cenário sintético. A leitura de ofertas do Uber Driver, as zonas no mapa, o histórico real de coleta e a avaliação pelo Jebadiah pertencem às próximas etapas. [PRD](docs/PRD.md) · [Specs](docs/features/README.md)
