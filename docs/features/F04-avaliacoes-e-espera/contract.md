# F04 — contrato de avaliações disponíveis e espera na retirada

**Fonte:** [PRD 0.2](../../PRD.md) · **Estado:** rascunho técnico para revisão. **Versão do contrato:** 0.1 · [Spec](spec.md) · [Plano](plan.md)

## Responsabilidades

O módulo F04 é dono da saída descrita abaixo. **Entradas obrigatórias:** [F01](../F01-dados-da-oferta/contract.md) **Consumidores:** F02 (opcional), F05 e F07. Os provedores internos estão especificados em rascunho, sem implementação verificada.

## Interface proposta

F04 consome nota, escala, papel, fonte e referência de unidade do snapshot F01, mais preferência local por papel e escala. Publica estado da nota, valor/fonte disponíveis, sinal passou, alerta, veto, desconhecido ou não aplicável; e espera da unidade, duração em segundos, contagem e período quando estimável. O registro local de coleta possui ID único, unidade confirmada, instante e duração não negativa. Veto requer nota abaixo do mínimo observada em escala compatível. Limite obrigatório sem nota produz pendência, sem veto.

## Compatibilidade e verificação

Os consumidores preservam a identidade da oferta conforme o contrato de F01 quando processarem uma oferta. Mudanças incompatíveis de unidades, campos obrigatórios, estados ou semântica exigem revisão dos provedores e consumidores listados. Os casos da [spec](spec.md#comportamento-e-aceite) exercitam a conformidade da saída e os caminhos de erro. O [plano](plan.md) indica a integração que deve exercitar o provedor real.
