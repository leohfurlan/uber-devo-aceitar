# Devo aceitar?

Protótipo Android do recomendador de corridas e entregas. As **Preferências** têm abas de **Metas**, **Veículo** e **Cartão**. Use **Teste** para ver como os ajustes afetam uma oferta de exemplo. Em **Veículo → Calculadora de lucro**, projete ganhos e custos por dia, semana e mês. **Aplicar metas** atualiza o rascunho de preferências e **Salvar** confirma a configuração.

## Build local

Use JDK 17 e Android SDK 35. Configure `ANDROID_HOME` para o SDK ou indique o caminho com `sdk.dir` em `local.properties`. Na raiz do projeto, execute `gradlew.bat :app:assembleDebug` no Windows ou `./gradlew :app:assembleDebug` em outro sistema. O APK de depuração fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Ensaio com dois apps no Android

1. Compile com `gradlew.bat :app:assembleDebug :lab:assembleDebug` no Windows ou `./gradlew :app:assembleDebug :lab:assembleDebug` em outro sistema. O segundo APK fica em `lab/build/outputs/apk/debug/lab-debug.apk`.
2. Instale ambos os APKs. Em **Configurações → Acessibilidade**, ative **Devo Aceitar? · modo laboratório**.
3. Abra **Laboratório de ofertas → Cenários → Entrega exclusiva** para reproduzir a oferta de R$ 39,34, 53 min e 27,4 km. A análise deve aparecer logo acima do cartão. Verifique se some ao aceitar, recusar, expirar ou sair do laboratório.
4. Toque em **Ficar online** para receber a primeira oferta sintética após 10 segundos. Cada oferta automática fica disponível por 8 segundos. A próxima começa a ser agendada depois de recusar, expirar ou encerrar a viagem em andamento.
5. Ao aceitar, use **Concluir simulação** para lançar o valor em **Ganhos**. Use **Cancelar simulação** para encerrar sem lançamento. Em **Mapa**, mova ou amplie o mapa esquemático por gestos.
6. Em **Cenários → Entrega em lote**, aceite a oferta e pause a primeira coleta. Marque a próxima como coletada, retome a primeira e conclua as duas coletas para liberar **Concluir simulação**. [Referência do fluxo de coleta](https://github.com/viks11021/uberEats-with-pause-feature/blob/main/PickupScreen.js)

O serviço de depuração identifica a janela do laboratório, lê os campos disponíveis da oferta na árvore de acessibilidade e calcula os resultados no aparelho. A janela da análise não recebe toques. O serviço está disponível apenas no build de depuração.

Em **Histórico de análises**, consulte as ofertas avaliadas pelo serviço do Laboratório, inclusive aquelas que expiraram ou não tiveram espaço para a sobreposição. Cada análise guarda a oferta, os parâmetros e o resultado no aparelho. Se os trechos estão disponíveis, o custo total inclui a ida até a coleta ou o embarque e o percurso até o destino. **Reavaliar com as preferências atuais** cria um novo registro vinculado ao original.

Use **Preferências → Teste** para visualizar o cartão enquanto ajusta as regras. A prévia de configuração não entra no histórico. O build `:app:assembleSimulator` oferece essa mesma prévia em um APK sem serviço de acessibilidade.

A regra de bairro compara o nome do destino com uma preferência local. A espera da coleta vem do cenário sintético. A leitura de ofertas do Uber Driver, as zonas no mapa, o histórico real de coleta e a avaliação pelo Jebadiah pertencem às próximas etapas. [PRD](docs/PRD.md) · [Specs](docs/features/README.md)
