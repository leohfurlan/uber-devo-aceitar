# F03 — contrato de preferências por bairro e zona

**Fonte:** [PRD 0.2](../../PRD.md) · **Estado:** rascunho técnico para revisão. **Versão do contrato:** 0.1 · [Spec](spec.md) · [Plano](plan.md)

## Responsabilidades

O módulo F03 é dono da saída descrita abaixo. **Entradas obrigatórias:** [F01](../F01-dados-da-oferta/contract.md) **Consumidores:** F05 e F07. Os provedores internos estão especificados em rascunho, sem implementação verificada.

## Interface proposta

F03 possui zoneId local, geometria ou bairro identificado por cidade, papéis, alerta/evitar, fuso e faixa opcional. Avaliar(snapshot F01, preferências da sessão) devolve por zona e papel: dentro, fora, desconhecida ou não aplicável; agrega vetos, alertas e obrigatoriedade pendente. Não guardar endereços da oferta no histórico de preferências. Cidade, fonte e revisão geográfica ficam no resultado de diagnóstico.

## Compatibilidade e verificação

Os consumidores preservam a identidade da oferta conforme o contrato de F01 quando processarem uma oferta. Mudanças incompatíveis de unidades, campos obrigatórios, estados ou semântica exigem revisão dos provedores e consumidores listados. Os casos da [spec](spec.md#comportamento-e-aceite) exercitam a conformidade da saída e os caminhos de erro. O [plano](plan.md) indica a integração que deve exercitar o provedor real.
