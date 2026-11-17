# Devo aceitar? — PRD para validar o recomendador de corridas e entregas

**Revisão 0.2 · 16/11/2026 · Rascunho para validação**

**Decisão de produto:** Leonardo. **Nome:** provisório. **Base:** decisões da conversa do projeto e contratos publicados dos dois Spaces usados como referência.

## Problema

Quando chega uma oferta, o motorista precisa decidir em poucos segundos. Para comparar o que recebe com o custo e o tempo, precisa somar ida até o embarque ou a coleta, trajeto principal e, quando relevante, espera na retirada. Também pode querer evitar um destino à noite ou ter um limite pessoal para a nota do passageiro. Se a oferta omite algum dado, a decisão fica mais difícil.

O DRIVH e o Mystro servem de referência para o cálculo por oferta e a configuração de zonas. A hipótese é que reunir **resultado financeiro, preferências de região e evidências sobre a contraparte** numa recomendação breve ajuda o motorista a decidir mais rápido e com mais clareza. O piloto deve medir o benefício de cada sinal e o ganho adicional do Jebadiah. [DRIVH](https://drivh.app/) · [Filtros por zona do Mystro](https://mystrodriver.com/help/filters)

## Experiência proposta

O motorista configura o custo por quilômetro, as faixas de rendimento e as regiões que prefere evitar. Em **Preferências → Teste do cartão**, uma oferta de exemplo responde aos ajustes antes de salvar. Quando o Uber Driver exibe uma oferta no Android, um **cartão flutuante aparece automaticamente logo acima do cartão da Uber**, na mesma tela. Em uma olhada, o motorista vê **Aceitar**, **Avaliar** ou **Recusar** e os indicadores escolhidos. A primeira análise usa os dados disponíveis no aparelho para aparecer quase instantaneamente. O motorista usa os controles da própria oferta para decidir.

> **Avaliar**\
> **R$ 1,44/km bruto · R$ 21,00/h líquido estimado**

Exemplo ilustrativo de entrega. A interface distingue o que a oferta informou, o que foi estimado e o que ficou indisponível.

### Escopo confirmado e hipóteses do piloto

- **Confirmado:** app independente com funções similares às referências, cálculos determinísticos, zonas configuráveis, avaliação do passageiro ou solicitante e da empresa ou estabelecimento em entregas, quando houver dados, e uso do Jebadiah na recomendação.
- **Confirmado:** exibir a análise quase instantaneamente em um cartão flutuante logo acima da oferta da Uber. Posição e tempo são critérios de aceite no Android.
- **Proposto:** iniciar com protótipo de ofertas anonimizadas ou sintéticas, depois piloto com Uber no Android em uma cidade. Validar primeiro corridas de passageiros e testar entregas assim que houver uma fonte de ofertas e dados de coleta autorizada. A avaliação de entregas já integra o protótipo.
- **Proposto:** aceitar ou recusar continua sendo uma ação manual. O Jebadiah atua em casos ambíguos e pode se abster. A indicação local funciona durante indisponibilidade ou atraso do modelo.

## Público e fluxo

O motorista ou entregador define suas preferências, consulta a indicação e decide sobre a oferta. O responsável pelo piloto prepara casos sem identificação pessoal, acompanha métricas agregadas e entrevista participantes. Cada motorista controla sua configuração e os próprios registros. Para o piloto, qualquer compartilhamento de dados de avaliação é optativo.

## Funcionalidades

| ID | Entrega | Ator | Prioridade proposta | Pré-requisitos | Fornece |
| --- | --- | --- | --- | --- | --- |
| F01 | Normalizar os dados da oferta | Motorista | Protótipo → piloto | Nenhum | Oferta com campos e disponibilidade explícitos |
| F02 | Calcular resultado financeiro | Motorista | Protótipo | F01 | Métricas e estimativas comparáveis |
| F03 | Aplicar preferências de região | Motorista | Protótipo | F01 | Alertas ou vetos por embarque, coleta ou destino |
| F04 | Usar avaliação e histórico operacional | Motorista e entregador | Protótipo → piloto | F01 | Nota disponível e estimativa de espera, quando sustentada |
| F05 | Mostrar a análise em cartão flutuante acima da oferta | Motorista e entregador | Protótipo → piloto | F02, F03, F04 | Indicação compacta para decidir na mesma tela |
| F06 | Avaliar casos ambíguos com Jebadiah | Motorista e equipe do piloto | Experimento | F05 | Segunda avaliação com opção de abstenção |
| F07 | Comparar opções em sessões de validação | Equipe do piloto | Validação | F05 | Evidência para decidir quais sinais avançam |

### F01 — dados da oferta

**História F01.1.** Como motorista, quero que o app entenda a oferta que recebi para avaliá-la antes de expirar.

- No protótipo, uma oferta pode ser reproduzida com dados de valor, distância e tempo por trecho, tipo de serviço, data e hora, regiões e avaliações, conforme disponibilidade.
- Quando um campo estiver ausente, ilegível ou ambíguo, fica marcado como desconhecido. Zero continua sendo um valor distinto de desconhecido.
- Na etapa com ofertas reais, uma oferta nova substitui a anterior. Depois de expirar, uma resposta atrasada não altera a tela.
- A captura real depende de um meio autorizado de leitura e de quais campos a plataforma oferece antes do aceite. Essa cobertura deve ser medida por cidade e tipo de oferta.

### F02 — resultado financeiro

**História F02.1.** Como motorista, quero comparar valor, distância, tempo e lucro estimado com minhas metas.

- A distância e o tempo somam o deslocamento até o embarque ou a coleta e o trajeto principal, sem duplicar totais já informados pela plataforma.
- O cálculo apresenta ganho bruto por km e por hora e, quando houver custo configurado, lucro estimado e resultado líquido por hora. Despesas como pedágio entram quando conhecidas.
- Em entregas, se F04 fornecer uma estimativa de espera ainda não incluída na oferta, o app mostra como ela afeta o resultado por hora. Tempo ou distância inválidos deixam as métricas dependentes indisponíveis.

### F03 — regiões evitadas

**História F03.1.** Como motorista, quero marcar bairros ou desenhar zonas e escolher se a regra vale para embarque, coleta ou destino.

- O motorista pode configurar uma região para **alertar** ou **evitar**, com opção de faixa de horário. Uma zona vale somente para os pontos e horários configurados.
- Uma zona obrigatória confirmada prevalece sobre a remuneração e sobre a sugestão do modelo. O cartão destaca o bairro afetado.
- Se o ponto ou o endereço for insuficiente para conferir uma zona aplicável, a interface informa “Região não verificada”. Nesta situação, uma regra obrigatória pendente leva a **Avaliar**, a menos que outro critério já justifique **Recusar**.
- A escolha de bairros pelo nome depende de cobertura de dados geográficos. O piloto compara a resolução por nome com zonas desenhadas no mapa.

### F04 — avaliação do passageiro, solicitante ou estabelecimento

**História F04.1.** Como motorista, quero considerar a nota disponível na oferta de acordo com minha preferência.

- Quando disponível, a nota pode aparecer como indicador. O motorista pode configurar alerta ou recusa para esse critério.
- Nota ausente aparece como indisponível e não é tratada como nota baixa. O bairro, o nome ou o endereço não servem para inferir a reputação de uma pessoa.

**História F04.2.** Como entregador, quero considerar a espera para retirar pedidos em determinada unidade.

- Quando identificada a unidade e houver registros próprios suficientes, o app mostra a espera estimada, a quantidade de registros e o período considerado. Histórico e nota do estabelecimento são sinais distintos.
- Com poucos registros, dados antigos ou unidade ambígua, o app sinaliza incerteza. A amostra mínima e o período serão definidos no piloto. O primeiro protótipo usa históricos de exemplo.

### F05 — cartão flutuante com recomendação imediata

**História F05.1.** Como motorista, quero ver os resultados logo acima da oferta da Uber, assim que ela chegar, para decidir na mesma tela.

- **Aceitar** quando os mínimos configurados forem atendidos, não houver veto e os critérios obrigatórios puderem ser verificados. **Avaliar** para alertas, dados essenciais incompletos ou resultados próximos do limite. **Recusar** quando uma regra obrigatória falhar ou a oferta ficar abaixo dos limites de recusa configurados. As faixas exatas serão calibradas no protótipo.
- O cartão mostra a decisão e até quatro indicadores disponíveis. Se houver veto por região, exibe o nome do bairro a evitar. A cor acompanha o texto e a análise mantém o motivo para validação.
- O teste em Preferências usa a mesma decisão e o mesmo cartão. Permite ver o efeito das faixas e da aparência antes de salvar. A duração configurada termina, no máximo, junto com a oferta.
- No piloto Android, o cartão compacto aparece automaticamente e imediatamente acima da borda superior da oferta da Uber, com um pequeno espaço entre os cartões. A tarifa, os detalhes e os controles da Uber permanecem visíveis e utilizáveis. O motorista pode dispensar a sobreposição.
- Se a posição da oferta não puder ser identificada com segurança ou não houver espaço acima dela, não exibir o cartão sobre a oferta. Registrar o caso como falha de cobertura de posicionamento no piloto.
- Exibir a primeira análise utilizável com a recomendação e as métricas disponíveis, sem esperar rede, Jebadiah ou o preenchimento de campos secundários. Meta inicial proposta: mediana de até **300 ms** e p95 de até **600 ms**, desde o primeiro quadro em que a oferta está visível até o primeiro quadro com os resultados legíveis. Um indicador de carregamento não conta como resultado.
- Associar o cartão somente à oferta vigente. Remover quando a oferta sumir, expirar, for aceita, for recusada ou o Uber Driver sair do primeiro plano. Na troca de oferta, retirar os resultados anteriores antes de mostrar os novos.

### F06 — recomendação complementar com Jebadiah

**História F06.1.** Como motorista, quero que o app considere casos em que os critérios apontam em direções diferentes.

- Com os dados suficientes e uma oferta em aberto, o modelo recebe somente os sinais necessários e devolve uma escolha entre **aceitar**, **avaliar**, **recusar** ou **evidência insuficiente**. A decisão exibida continua respeitando os vetos e os requisitos de F05.
- Se a saída for inválida, inconclusiva, atrasada ou indisponível, a indicação local continua disponível. A justificativa registrada usa fatos da oferta e motivos reconhecidos pelo app.
- No piloto inicial, a avaliação do modelo roda em paralelo sem alterar o cartão. Ela só passa a influenciar a indicação após demonstrar ganho sobre as regras locais e respeitar o tempo da oferta.
- O contrato tipado e o tratamento de abstenção seguem os padrões observados nos Spaces. A fila ZeroGPU serve como referência e opção para ensaios. Os Spaces estavam em repouso na inspeção e não há medida de latência para corridas em tempo real. [Mater Dei](https://huggingface.co/spaces/furlanhleo/materdei-audit-jebadiah) · [Talk2Me](https://huggingface.co/spaces/furlanhleo/talk2me-jebadiah-zerogpu) · [Choice](https://docs.typesafe.ai/primitives/choice)

### F07 — validação com motoristas

**História F07.1.** Como responsável pelo piloto, quero saber se regiões, avaliações e Jebadiah melhoram decisões em relação ao cálculo financeiro básico.

- A equipe consegue comparar, usando ofertas equivalentes, três versões: **A** com cálculos financeiros, **B** com cálculos, zonas e avaliação, e **C** com avaliação complementar do Jebadiah. Alternar a ordem de apresentação reduz o efeito de prática.
- A avaliação cobre, no mínimo, zona por destino, limite por nota, nota ausente, destino ilegível, atraso na retirada, conflito entre ganho e preferência, perda de conexão, oferta substituída e resposta tardia.
- O relatório discrimina corridas e entregas, aponta cobertura dos campos, erros observados e registra a escolha do motorista e seu motivo. Para julgar aderência à preferência, a referência é a decisão informada do próprio motorista com tempo para revisar a oferta.

## Ondas de entrega

1. **F01 — ofertas reproduzíveis.** Definir os dados exibidos e desconhecidos com exemplos de corridas e de entregas. O mesmo formato serve aos avaliadores locais.
2. **F02, F03 e F04 — sinais.** Depois de F01, calcular rendimento, aplicar preferências geográficas e representar nota e espera. F04 pode enriquecer F02 com espera, mas F02 calcula o cenário básico sozinho.
3. **F05 — protótipo de decisão.** Combinar os três sinais e testar a leitura do cartão compacto acima de uma oferta reproduzida. No piloto Android, verificar posição, tempo e uso dos controles com ofertas reais quando F01 tiver cobertura suficiente.
4. **F06 e F07 — comparação.** F07 pode avaliar A e B enquanto F06 é preparado. A comparação com C começa quando houver resultados do modelo sobre os mesmos cenários.

## Plano de validação e critérios para avançar

**Ponto de partida proposto:** entrevistar de 6 a 10 motoristas e entregadores e usar ao menos 40 ofertas variadas, com casos normais e de borda. Começar com sessões de reprodução cronometradas, com os participantes fora da condução. A amostra é exploratória e os resultados devem incluir contagens e diferenças por perfil.

| Pergunta | Medida | Condição proposta |
| --- | --- | --- |
| A análise aparece quase instantaneamente? | Tempo entre o primeiro quadro visível da oferta e a primeira análise legível. Medir com gravação de tela ou instrumentação equivalente no Android, incluindo a primeira oferta após ativar o recurso. | Meta inicial de mediana até 300 ms e p95 até 600 ms entre as ofertas renderizadas. Reportar também quantas ofertas tiveram análise utilizável antes de expirar, inclusive as que ficaram sem cartão. Objetivo inicial de pelo menos 90% das ofertas observadas com campos financeiros legíveis. |
| O cartão aparece no lugar certo? | Proporção de ofertas com cartão acima da oferta e ocorrências de controles cobertos ou de cartão associado à oferta errada. Verificar em aparelhos de tamanhos diferentes e nos layouts de oferta encontrados no piloto. | Meta inicial de pelo menos 95% de posicionamento correto entre ofertas com campos financeiros legíveis e zero ocorrências de controles cobertos ou bloqueados. Registrar ausência de espaço e posição indeterminada como falhas de cobertura. |
| Zonas e avaliação ajudam a decidir? | Tempo de decisão, conflitos com preferências e compreensão do motivo, comparando B e A | B reduz a mediana do tempo em 20% sem aumentar os conflitos com critérios obrigatórios. Pelo menos 80% dos participantes identificam o motivo dominante nos casos em que o cartão o exibe. |
| O modelo melhora casos ambíguos? | Aderência à decisão informada do motorista, benefício em relação a B, abstenções e respostas dentro da janela | Buscar melhora inicial de pelo menos 10 pontos percentuais em casos ambíguos. Registrar o tamanho da amostra e a incerteza. Aprovar uso ao vivo somente após medir latência em rede real e confirmar margem para o motorista decidir. |
| As regras obrigatórias continuam íntegras? | Vetos ignorados, campos desconhecidos tratados como confirmados e resultados de oferta expirada apresentados | Zero ocorrências na bateria definida. Qualquer ocorrência exige corrigir a causa antes de avançar. |

**Decisão após o piloto:** avançar com B se o cartão for rápido, compreensível e fiel às preferências. Se apenas alguns campos tiverem cobertura suficiente, restringir o piloto aos cenários verificados. Ativar C ao vivo só se acrescentar valor mensurável no tempo disponível. Resultados econômicos ao longo do turno exigem estudo posterior e comparação que considere tempo parado e deslocamento vazio.

## Fora do primeiro piloto

Aceite ou recusa automática, troca automática entre plataformas, integração com Mystro, ranking público de passageiros, compartilhamento de listas de bloqueio, previsão de segurança de pessoas ou bairros e expansão para várias cidades ou plataformas.

## Decisões abertas para a revisão

| Decisão | Proposta para discussão | Efeito |
| --- | --- | --- |
| Cidade e plataforma inicial | Uma cidade, Uber no Android, com motoristas que possam trazer exemplos de ofertas | Determina dados e cobertura geográfica a medir |
| Plataforma de entregas e fonte da avaliação | Priorizar disponibilidade antes do aceite e histórico próprio de espera por unidade | Determina se F04 pode passar do protótipo ao piloto real |
| Horários e faixas financeiras padrão | Usar preferências escolhidas nas entrevistas e permitir ajuste | Mudam os motivos e a recomendação local |
| Infraestrutura de inferência | Comparar o tempo disponível da oferta com os tempos de fila, rede e inferência | Determina se Jebadiah entra na janela ao vivo ou fica em avaliação paralela |
| Amostra, metas e critérios de aprovação | Revisar os números deste documento com Leonardo após a primeira rodada | Define quais sinais passam para uma versão de uso contínuo |

## Handoff para especificação

A primeira especificação candidata é **F01**, usando esta revisão 0.2 e exemplos de ofertas sintéticas ou anonimizadas. Ela deve fixar o que conta como observado, estimado e desconhecido, além de como identificar a oferta vigente. A especificação de **F05** detalha posicionamento, remoção e medição da latência do cartão. O primeiro fluxo de validação completo reúne **F01 a F05 e F07**. **F06** recebe especificação própria a partir dos casos ambíguos desse fluxo.
