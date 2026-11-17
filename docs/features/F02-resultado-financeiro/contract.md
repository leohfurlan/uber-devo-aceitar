# F02 — contrato de resultado financeiro determinístico

**Fonte:** [PRD 0.2](../../PRD.md) · **Estado:** rascunho técnico para revisão. **Versão do contrato:** 0.1 · [Spec](spec.md) · [Plano](plan.md)

## Responsabilidades

O módulo F02 é dono da saída descrita abaixo. **Entradas obrigatórias:** [F01](../F01-dados-da-oferta/contract.md) **Consumidores:** F05 e F07. Os provedores internos estão especificados em rascunho, sem implementação verificada.

## Interface proposta

avaliarFinancas(snapshot F01, custoCentavosPorKm opcional, espera F04 opcional) devolve métricas opcionais em BRL/km, BRL/h e centavos, com bases e lacunas. Bruto/km = pagamento ÷ distância total. Bruto/h = pagamento ÷ duração total. Lucro estimado = pagamento − custo por km × distância − despesas aplicáveis conhecidas ainda não deduzidas. Líquido/h = lucro estimado ÷ duração. Duração ajustada = duração base + espera válida não incluída. Resultado acompanha offerId e revisão via snapshot. Diferença de unidade ou tarifa ambígua invalida apenas o que dela depende.

## Compatibilidade e verificação

Os consumidores preservam a identidade da oferta conforme o contrato de F01 quando processarem uma oferta. Mudanças incompatíveis de unidades, campos obrigatórios, estados ou semântica exigem revisão dos provedores e consumidores listados. Os casos da [spec](spec.md#comportamento-e-aceite) exercitam a conformidade da saída e os caminhos de erro. O [plano](plan.md) indica a integração que deve exercitar o provedor real.
