# Especificações do Devo aceitar?

**Fonte:** [PRD 0.2](../PRD.md). **Estado:** rascunho técnico para revisão. O workspace tinha apenas documentação. Stack, contratos e comandos de qualidade estão propostos.

| Onda | Funcionalidade | Artefatos |
| --- | --- | --- |
| 1 | F01 · Dados e ciclo de vida da oferta | [Spec](F01-dados-da-oferta/spec.md) · [Contrato](F01-dados-da-oferta/contract.md) · [Plano](F01-dados-da-oferta/plan.md) |
| 2 | F02 · Resultado financeiro determinístico | [Spec](F02-resultado-financeiro/spec.md) · [Contrato](F02-resultado-financeiro/contract.md) · [Plano](F02-resultado-financeiro/plan.md) |
| 2 | F03 · Preferências por bairro e zona | [Spec](F03-zonas/spec.md) · [Contrato](F03-zonas/contract.md) · [Plano](F03-zonas/plan.md) |
| 2 | F04 · Avaliações disponíveis e espera na retirada | [Spec](F04-avaliacoes-e-espera/spec.md) · [Contrato](F04-avaliacoes-e-espera/contract.md) · [Plano](F04-avaliacoes-e-espera/plan.md) |
| 3 | F05 · Cartão flutuante e recomendação imediata | [Spec](F05-cartao-flutuante/spec.md) · [Contrato](F05-cartao-flutuante/contract.md) · [Plano](F05-cartao-flutuante/plan.md) |
| 4 | F06 · Avaliação complementar com Jebadiah | [Spec](F06-jebadiah/spec.md) · [Contrato](F06-jebadiah/contract.md) · [Plano](F06-jebadiah/plan.md) |
| 4 | F07 · Validação com motoristas | [Spec](F07-validacao-do-piloto/spec.md) · [Contrato](F07-validacao-do-piloto/contract.md) · [Plano](F07-validacao-do-piloto/plan.md) |

## Dependências

F01 fornece a identidade e os dados da oferta para F02, F03 e F04. Esses três fornecem sinais a F05, que sustenta F06 e F07. F04 pode adicionar espera a F02. F06 é pré-requisito da comparação C em F07. A comparação A/B usa apenas a primeira análise local. A atualização ao vivo por F06, se aprovada, é consumida pelo cartão por meio da arbitragem local de F05. A primeira renderização de F05 independe de F06.

## Decisões para fechar antes de cada piloto

- **F01/F05.** Confirmar a cidade, os aparelhos e a forma autorizada de obter os campos e o retângulo da oferta. Kotlin e captura pela árvore de acessibilidade são propostas. A compatibilidade depende de verificar ofertas e layouts reais. [AccessibilityService do Android](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) · [Política do Google Play](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- **F02/F03/F04.** Acordar tolerância de métricas, fonte e resolução de bairro, limiares financeiros, fonte da nota e critérios de amostra de espera. Montar os primeiros cenários com dados sintéticos e parâmetros explícitos.
- **F06.** Validar critério de ambiguidade, revisão e confidência do modelo, prazo e hospedagem. Escolher os dados de calibração sem usar os exemplos reservados à avaliação. [Choice](https://docs.typesafe.ai/primitives/choice)
- **F07.** Confirmar recrutamento, consentimento e retenção, volume de casos, critérios de decisão e condições de comparação. Metas e números do PRD são hipóteses de piloto.

## Qualidade e evidências

| Gate | Evidência atual | Comando e diretório | Pré-requisito e escopo | Política e execução |
| --- | --- | --- | --- | --- |
| Compilação e lint Android | Sem configuração de build | Proposta: `./gradlew assembleDebug lintDebug` na futura raiz Android (`gradlew.bat` no Windows) | Projeto Android, wrapper Gradle e SDK configurados | Proposto, não executado |
| Testes locais de contratos e regras | Sem suítes configuradas | Proposta: `./gradlew testDebugUnitTest` na futura raiz Android | Contratos acordados e amostras sintéticas, F01–F07 conforme aplicável | Proposto, não executado |
| Integração em dispositivo | Sem suíte de instrumentação | Proposta: `./gradlew connectedDebugAndroidTest` na futura raiz Android, complementado por ensaio em dispositivo | Build, aparelho e dados autorizados. Verificar ciclo e sobreposição, interação e latência em F01/F05 | Proposto, não executado |
| Revisão do piloto | Critérios do PRD 0.2 | Protocolo F07 e relatório de resultados | Reprodução A/B, comparação C quando F06 estiver disponível e ensaio real para medir cobertura | Proposto, não executado |
