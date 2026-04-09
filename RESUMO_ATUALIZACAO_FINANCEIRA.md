# Resumo da Atualização Financeira - Clube do Dominó

Este documento serve como ponto de controle para continuar o trabalho.

## Estado Atual (07/04/2026)

### O que foi implementado:
1.  **Versão v46 (Android)**: A lógica de checagem retroativa e cálculo de taxa extra do cliente foi **totalmente removida**. O app e o PWA agora se limitam a **disparar a API central no n8n** (como um POST), o que transfere toda a responsabilidade de calcular médias e gerar as cobranças precisas (buchos) diretamente no servidor.
2.  **PWA**: O `index.html` também teve o código complexado removido e agora apenas envia uma requisição `{}` via POST para acionar o fluxo n8n.
3.  **Maior Segurança e Escalabilidade**: O n8n tem os dados centralizados para conferir as partidas do clube versus as partidas dos jogadores em um só local, não havendo mais divergências ou atrasos locais gerando taxas indevidas ou não gerando.

### Próximos Passos:
- **Monitorar Atribuição Pelo N8N**: Validar se as novas cobranças chegam sem duplicidades nos painéis dos jogadores ao abrir o jogo na aba de transações.

---

## Log de Commits Recentes
- `d24448c`: Remove client-side extra fee logic, rely on POST trigger to n8n API (v46)
- `9323cd0`: Implement n8n simplified tax logic using GET method and upgrade to v45
- `0e60eb5`: Lógica de taxa integral para jogadores ausentes e bump para v44
