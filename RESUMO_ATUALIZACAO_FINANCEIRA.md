# Resumo da Atualização Financeira - Clube do Dominó

Este documento serve como ponto de controle para continuar o trabalho em outro computador.

## Estado Atual (07/04/2026)

### O que foi implementado:
1.  **Versão v43 (Android)**: O app agora possui lógica retroativa para **Mensalidades** e **Taxas Extras**.
2.  **PWA**: O `index.html` também foi atualizado com a mesma lógica retroativa.
3.  **Parser de Datas**: Implementei um sistema robusto que entende datas no formato `DD/MM/YYYY` (brasileiro) e `YYYY-MM-DD`. Isso resolveu o problema de o sistema não reconhecer cobranças antigas.

### Bloqueio Atual:
O cálculo da **Taxa Extra** (média de jogos do clube e média de buchos sofridos) ainda apresenta imprecisões quando feito localmente no celular/PWA. Isso ocorre porque o app nem sempre tem acesso a todo o histórico de partidas do clube.

### Próximos Passos:
- **Aguardar API Customizada**: O usuário está criando um endpoint no N8N que devolverá os valores de `média de partidas` e `valor médio de buchos` já calculados por mês.
- **Integração**: Assim que o endpoint estiver pronto, devemos atualizar o `FinanceViewModel.kt` e o `index.html` para consultar esses valores antes de decidir gerar a taxa extra.

---

## Log de Commits Recentes
- `9f495d9`: Corrigido parser de datas brasileiro (DD/MM/YYYY) e finalizado Taxa Extra retroativa v43
- `8577608`: Implementado lógica financeira retroativa (Jan, Feb, Mar) no Android e PWA v42
