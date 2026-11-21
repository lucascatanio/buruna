# Feature Specification: Migrar o backend para o Spring Boot 4

**Feature Branch**: `chore/124-spring-boot-4`

**Created**: 2026-11-21

**Status**: Draft

**Issue**: [#124](https://github.com/lucascatanio/buruna/issues/124)

**Input**: User description: "#124: migrar o backend de Spring Boot 3.5.16 para 4.1.3 (Framework 7.0.11, Security 7.1.3). A linha 3.5 não recebe mais correção de segurança gratuita; os avisos de 2026-11-19 e os CVE-2026-47884/47890 do spring-webmvc só têm correção aberta no Boot 4. O comportamento do app para o usuário não muda: login, leitura, upload, import e os jobs do Cloud Scheduler continuam funcionando igual."

## Contexto

O backend roda numa linha da plataforma que não recebe mais correção de segurança aberta.
A varredura de vulnerabilidades aponta duas falhas críticas (CVE-2026-47884 e
CVE-2026-47890) na camada web, e os avisos publicados em 2026-11-19 também só têm correção
na linha nova. Os ajustes pontuais de versão que o projeto vinha fazendo (sobrescrever
bibliotecas sem trocar a plataforma) não alcançam essas falhas: a correção só existe na
versão maior seguinte.

A mudança é de manutenção. Ninguém que usa o Burūna deve perceber diferença: cada fluxo que
funciona hoje continua igual depois do deploy.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Quem lê não percebe a troca (Priority: P1)

Um leitor que já tinha conta entra no site depois do deploy. Continua logado se já estava,
ou entra com e-mail e senha como antes. Abre a biblioteca, continua um capítulo de onde
parou, lê um PDF e um EPUB, avalia uma obra e mexe na lista de leitura. Nada muda: mesmas
telas, mesmas mensagens, mesmo progresso salvo.

**Why this priority**: é o uso de quase todo mundo no site. Se a leitura ou o login
quebram, o deploy volta atrás.

**Independent Test**: com o backend migrado, uma conta existente faz login, abre um capítulo
de cada formato, avança páginas e confere que o progresso e as avaliações de antes estão lá.

**Acceptance Scenarios**:

1. **Given** um leitor logado antes do deploy, **When** ele volta ao site depois do deploy,
   **Then** a sessão continua válida e ele não precisa entrar de novo.
2. **Given** uma conta existente, **When** o leitor entra com e-mail e senha, **Then** o
   login funciona e a renovação silenciosa da sessão continua funcionando.
3. **Given** um capítulo em PDF, um em imagens e um livro em EPUB já publicados, **When** o
   leitor abre cada um, **Then** as páginas carregam e o progresso é salvo como antes.
4. **Given** um leitor sem permissão, **When** ele tenta uma ação restrita (área de
   administração, obra privada de outra pessoa), **Then** recebe a mesma recusa de hoje.
5. **Given** um pedido inválido (formulário incompleto, recurso inexistente), **When** o
   backend responde, **Then** o erro tem o mesmo formato e a mesma mensagem de hoje.

---

### User Story 2 - Quem publica e quem administra também não percebe (Priority: P2)

Uma pessoa envia um capítulo novo (PDF, imagens ou EPUB) a uma obra dela. O envio sobe, o
processamento em segundo plano extrai as páginas e o capítulo aparece. O ADM importa uma
obra de uma fonte externa, acompanha a importação e aprova cadastros pendentes. Tudo como
antes.

**Why this priority**: é o fluxo que mais depende da integração com armazenamento e com o
processamento fora da requisição, onde a troca de plataforma tem mais chance de quebrar
algo sem aparecer nos testes de leitura.

**Independent Test**: enviar um capítulo de cada formato e importar uma série curta de uma
fonte externa; os capítulos aparecem com as páginas certas.

**Acceptance Scenarios**:

1. **Given** uma obra da pessoa, **When** ela envia um capítulo em PDF, em imagens e em
   EPUB, **Then** cada um é aceito, processado e publicado como antes.
2. **Given** um arquivo hostil (tipo falso, tamanho acima do limite), **When** ele é
   enviado, **Then** a recusa é a mesma de hoje.
3. **Given** uma obra numa fonte externa, **When** o ADM a importa, **Then** os capítulos
   são baixados e publicados como antes.
4. **Given** um cadastro pendente, **When** o ADM o aprova, **Then** a conta passa a entrar.
5. **Given** um pedido de redefinição de senha, **When** a pessoa o faz, **Then** o e-mail
   chega e o link redefine a senha.

---

### User Story 3 - As rotinas agendadas continuam rodando (Priority: P3)

Ninguém dispara essas rotinas à mão: a limpeza de contas inativas (diária), a limpeza de
arquivos órfãos (semanal) e a sincronização com as fontes externas (a cada 3 horas). Depois
do deploy, cada uma roda no horário de sempre e faz o mesmo trabalho.

**Why this priority**: falha aqui não aparece na tela, só dias depois (capítulo que não
chega, arquivo que acumula). Por isso precisa de conferência explícita depois do deploy.

**Independent Test**: disparar cada rotina uma vez depois do deploy e conferir o resultado
registrado; esperar a próxima execução agendada da sincronização e conferir que rodou.

**Acceptance Scenarios**:

1. **Given** o backend migrado em produção, **When** cada rotina agendada é disparada,
   **Then** ela conclui com sucesso e o resultado registrado é o mesmo de antes da troca.
2. **Given** um disparo de rotina sem a credencial certa, **When** ele chega ao backend,
   **Then** é recusado como hoje.
3. **Given** uma sincronização rodada duas vezes seguidas, **When** a segunda termina,
   **Then** não há capítulo duplicado nem corrompido.

---

### Edge Cases

- Deploy com sessão em andamento: o token de quem estava logado e o cookie de renovação
  emitidos antes do deploy continuam aceitos depois.
- Processamento em segundo plano disparado antes do deploy e ainda rodando: termina com a
  versão antiga, e o seguinte já usa a nova; reexecutar não duplica nada.
- Datas, números e campos vazios nas respostas: a troca de plataforma pode mudar o formato
  padrão de serialização. O frontend recebe exatamente o mesmo formato de hoje.
- Respostas de erro geradas pela própria plataforma (rota inexistente, método não
  permitido, corpo mal formado): continuam no formato de erro do projeto, sem página padrão
  da plataforma nem detalhe interno vazando.
- Cabeçalhos de segurança, CORS e a política de cookies: continuam iguais.
- Problema grave só visto em produção: voltar para a revisão anterior resolve, sem passo
  manual no banco.
- Dependência do projeto que ainda não suporta a linha nova: a migração para e o problema
  volta para a spec antes de seguir com versão incompatível.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O backend MUST rodar numa linha da plataforma que ainda recebe correção de
  segurança aberta, na versão que corrige CVE-2026-47884, CVE-2026-47890 e os avisos de
  2026-11-19.
- **FR-002**: A varredura de vulnerabilidades da imagem do backend MUST não apontar nenhuma
  vulnerabilidade crítica depois da migração.
- **FR-003**: Sobrescritas de versão que o projeto mantinha para corrigir falhas e que a
  linha nova já cobre MUST ser removidas. As que continuarem necessárias ficam com o motivo
  registrado ao lado.
- **FR-004**: Login, cadastro, aprovação de cadastro, renovação de sessão, saída e
  redefinição de senha MUST funcionar como hoje, inclusive para sessões abertas antes do
  deploy.
- **FR-005**: Leitura (capítulos em PDF, imagens e EPUB), progresso, avaliações, lista de
  leitura e acompanhamento MUST funcionar como hoje.
- **FR-006**: Envio de capítulos e o processamento em segundo plano MUST funcionar como
  hoje, com as mesmas validações e recusas de arquivo.
- **FR-007**: Importação e sincronização com fontes externas MUST funcionar como hoje.
- **FR-008**: As três rotinas agendadas (inatividade, arquivos órfãos, sincronização) e o
  aviso de redefinição de senha recebido por mensageria MUST funcionar como hoje e recusar
  chamadas sem a credencial certa.
- **FR-009**: Toda resposta da API MUST manter o mesmo formato de hoje: mesmos campos,
  mesmo formato de data, mesmo tratamento de campo vazio e o mesmo formato de erro.
- **FR-010**: As regras de acesso MUST continuar iguais: quem podia fazer algo continua
  podendo, e quem era recusado continua recusado, com o mesmo status.
- **FR-011**: Cabeçalhos de segurança, CORS, política de cookies e a verificação de saúde
  usada pelo provedor MUST continuar iguais.
- **FR-012**: A migração MUST não alterar o esquema do banco, para que voltar à versão
  anterior seja só trocar a revisão em produção.
- **FR-013**: Toda a suíte automatizada existente MUST passar, inclusive o teste das
  fronteiras entre contextos, sem afrouxar nem apagar teste para fazer passar.
- **FR-014**: O frontend MUST não precisar de mudança.
- **FR-015**: Os documentos que citam a versão da plataforma (constituição, README e docs
  de referência) MUST ser atualizados no mesmo PR.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero vulnerabilidades críticas na varredura da imagem do backend, e os dois
  CVEs da issue fora da lista.
- **SC-002**: 100% da suíte automatizada do backend passa, com o mesmo número de testes ou
  mais que antes da migração.
- **SC-003**: Em produção, depois do deploy, os fluxos de login, leitura, envio,
  importação e as três rotinas agendadas são conferidos um a um e todos funcionam na
  primeira tentativa.
- **SC-004**: Nenhum leitor logado antes do deploy precisa entrar de novo por causa dele.
- **SC-005**: Na primeira semana depois do deploy, nenhum relato de erro ou mudança de
  comportamento atribuível à migração.
- **SC-006**: Voltar para a revisão anterior, se preciso, leva menos de 5 minutos e não
  exige passo no banco.

## Assumptions

- Versões-alvo: Spring Boot 4.1.3, com Spring Framework 7.0.11 e Spring Security 7.1.3. As
  demais dependências principais que a linha nova arrasta (serialização JSON, ORM,
  migrations, testes de integração) sobem para as versões que ela gerencia. Qual versão de
  cada uma e como adaptar o código é decisão do plano.
- Java 21 continua. Subir a versão do Java fica fora desta feature.
- Escopo é só manter o comportamento. Recurso novo da plataforma (versionamento de API,
  anotações de nulidade, starters modulares) só entra se a migração exigir.
- Nenhuma mudança de infraestrutura (serviço, Job, agendamentos, segredos, rede) é
  necessária. Se o plano encontrar uma, ela entra no DEPLOYMENT.md no mesmo PR.
- A varredura de vulnerabilidades é a mesma ferramenta já usada no projeto (Trivy), rodada
  à mão na imagem, já que ela não está no CI.
- A troca da linha "Spring Boot 3.5" na constituição é emenda PATCH (só redação de versão,
  nenhuma regra muda).
- Itens que só dá para conferir em produção (rotinas agendadas, mensageria, credenciais
  reais) ficam numa seção "depois do deploy" do quickstart.
- A main de produção recebe a mudança pelo fluxo normal de release, com nova versão do app.
