# F01 — contrato de dados e ciclo de vida da oferta

**Fonte:** [PRD 0.2](../../PRD.md) · **Estado:** rascunho técnico para revisão. **Versão do contrato:** 0.1 · [Spec](spec.md) · [Plano](plan.md)

## Responsabilidades

O módulo F01 é dono da saída descrita abaixo. **Entradas obrigatórias:** Nenhum requisito interno anterior. **Consumidores:** F02, F03, F04, F05 e F07. Os provedores internos estão especificados em rascunho, sem implementação verificada.

## Interface proposta

F01 possui IDs locais efêmeros e a ordem de observação. Cada sessão tem no máximo uma oferta ativa. Eventos snapshot e encerrada carregam sessionId, offerId, seq crescente e instante monotônico. O snapshot é completo, tem revisão crescente para o mesmo ID e pode ter prazo monotônico conhecido. Ao trocar de oferta, encerrar a anterior antes de publicar a nova. Se o prazo não for conhecido, usar o ciclo observado. A reabertura exige novo ID.

Snapshot: tipo corrida ou entrega; plataforma; instante civil com fuso; pagamento e despesas em centavos BRL; distância em metros e duração em segundos por escopo total, aproximação, percurso e espera quando explicitada; pontos por papel embarque/coleta/destino; notas por papel e escala de origem; referência opcional e local da unidade; retângulo da oferta em pixels da janela visível. Cada valor de origem é observado, estimado ou desconhecido com motivo ausente, inválido, ambíguo ou ilegível. Estimativa exige método identificado. Campos externos são opcionais e normalizados para estado explícito. Negativos em valores que exigem magnitude não passam. Nome de pessoa e conteúdo bruto da tela ficam fora do contrato.

## Compatibilidade e verificação

Os consumidores preservam a identidade da oferta conforme o contrato de F01 quando processarem uma oferta. Mudanças incompatíveis de unidades, campos obrigatórios, estados ou semântica exigem revisão dos provedores e consumidores listados. Os casos da [spec](spec.md#comportamento-e-aceite) exercitam a conformidade da saída e os caminhos de erro. O [plano](plan.md) indica a integração que deve exercitar o provedor real.
