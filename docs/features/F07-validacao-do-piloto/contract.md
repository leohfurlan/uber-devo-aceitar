# F07 — contrato de validação com motoristas

**Fonte:** [PRD 0.2](../../PRD.md) · **Estado:** rascunho técnico para revisão. **Versão do contrato:** 0.1 · [Spec](spec.md) · [Plano](plan.md)

## Responsabilidades

O módulo F07 é dono da saída descrita abaixo. **Entradas obrigatórias:** [F05](../F05-cartao-flutuante/contract.md) **Consumidores:** Responsável pelo piloto. Os provedores internos estão especificados em rascunho, sem implementação verificada.

## Interface proposta

F07 possui IDs de sessão e caso pseudônimos. Registro mínimo: modalidade, variante A/B/C, versão de configuração e modelo quando aplicável, instante inicial, latência até cartão, motivo de cartão ausente, indicação, escolha, tempo para escolher, motivo informado e referência após revisão. Tempo sem cartão é ausente com motivo. Relatório agrega por variante e modalidade, incluindo numerador, denominador, amostra ambígua e condições de coleta. Dados individuais optativos seguem retenção definida antes do piloto.

## Compatibilidade e verificação

Os consumidores preservam a identidade da oferta conforme o contrato de F01 quando processarem uma oferta. Mudanças incompatíveis de unidades, campos obrigatórios, estados ou semântica exigem revisão dos provedores e consumidores listados. Os casos da [spec](spec.md#comportamento-e-aceite) exercitam a conformidade da saída e os caminhos de erro. O [plano](plan.md) indica a integração que deve exercitar o provedor real.
