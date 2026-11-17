# F05 — contrato de cartão flutuante e recomendação imediata

**Fonte:** [PRD 0.2](../../PRD.md) · **Estado:** rascunho técnico para revisão. **Versão do contrato:** 0.1 · [Spec](spec.md) · [Plano](plan.md)

## Responsabilidades

O módulo F05 é dono da saída descrita abaixo. **Entradas obrigatórias:** [F02](../F02-resultado-financeiro/contract.md), [F03](../F03-zonas/contract.md), [F04](../F04-avaliacoes-e-espera/contract.md) **Consumidores:** F06 e F07. Os provedores internos estão especificados em rascunho, sem implementação verificada.

## Interface proposta

O [contrato de F01](../F01-dados-da-oferta/contract.md) fornece diretamente a área da oferta, a identidade vigente e os eventos de encerramento ao renderizador. Essa interface também é usada na reprodução.

Decisão local recebe snapshot vigente, saídas F02/F03/F04 e preferências com revisão. Produz offerId, revisão da oferta e da política, indicação aceitar/avaliar/recusar, motivos de código fechado e métricas de F02. Comparar gatilhos determinísticos por conjunto, com piso de recusa explícito e separado do mínimo de aceite. Valores entre ambos são intermediários. Ordem proposta de motivo principal: veto de zona, veto de nota, piso financeiro, pendência, alerta e atendimento às metas. O renderizador só exibe decisão da oferta e revisão atuais, dentro da área segura, integralmente acima da oferta e sem sobreposição de controles. Se invisível, emitir motivo categórico para medir cobertura.

## Compatibilidade e verificação

Os consumidores preservam a identidade da oferta conforme o contrato de F01 quando processarem uma oferta. Mudanças incompatíveis de unidades, campos obrigatórios, estados ou semântica exigem revisão dos provedores e consumidores listados. Os casos da [spec](spec.md#comportamento-e-aceite) exercitam a conformidade da saída e os caminhos de erro. O [plano](plan.md) indica a integração que deve exercitar o provedor real.
