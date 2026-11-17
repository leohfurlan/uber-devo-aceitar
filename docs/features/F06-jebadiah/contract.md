# F06 — contrato de avaliação complementar com jebadiah

**Fonte:** [PRD 0.2](../../PRD.md) · **Estado:** rascunho técnico para revisão. **Versão do contrato:** 0.1 · [Spec](spec.md) · [Plano](plan.md)

## Responsabilidades

O módulo F06 é dono da saída descrita abaixo. **Entradas obrigatórias:** [F05](../F05-cartao-flutuante/contract.md) **Consumidores:** F07 e, se aprovado, F05 via arbitragem local. Os provedores internos estão especificados em rascunho, sem implementação verificada.

## Interface proposta

Interface lógica avaliar(requestId, versão da tarefa, id opaco da oferta, revisão dos dados e preferências, indicadores normalizados, flags de conflito e prazo restante). O cliente controla o prazo monotônico local e descarta resultados expirados. O retorno ecoa requestId e versão, identifica modelo/revisão e retorna escolha e distribuição completa, ou estado abstenção, indisponível, inválido ou expirado. Distribuição finita, valores de 0 a 1, soma 1 com tolerância proposta de 0,00001 e máximo único coerente com a escolha. Quarto rótulo: evidência insuficiente. Sem nomes, endereços, coordenadas ou conteúdo bruto. Uma tentativa por revisão em modo ao vivo, sem repetição síncrona. Limiar e margem são calibrados em dados separados da avaliação. Transporte e autenticação do gateway aguardam escolha de hospedagem.

## Compatibilidade e verificação

Os consumidores preservam a identidade da oferta conforme o contrato de F01 quando processarem uma oferta. Mudanças incompatíveis de unidades, campos obrigatórios, estados ou semântica exigem revisão dos provedores e consumidores listados. Os casos da [spec](spec.md#comportamento-e-aceite) exercitam a conformidade da saída e os caminhos de erro. O [plano](plan.md) indica a integração que deve exercitar o provedor real.
