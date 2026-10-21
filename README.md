# Painel

App Android que reúne o que importa no dia, numa grade de blocos de vidro sobre um fundo escuro com manchas de cor.

- **Clima** de Horizonte (CE): agora e próximos dias.
- **E-mails** do Gmail recebidos nas últimas 24 h.
- **Importante hoje:** principais notícias do g1 e da BBC Brasil.
- **Fortaleza:** próximo jogo (ou placar ao vivo), último resultado e notícias.
- **Games:** notícias de videogames.
- **Agenda:** compromissos das agendas do celular (inclui o Google Agenda).
- **Atalhos** para os outros apps: Palavreiro, Matinê, Leão, Estante, Sintonia e Soberano.

## Personalizar

Cada bloco tem um botão de ajuste:

- **Tamanho:** Pequeno (meia largura), Médio (largura toda) ou Grande (largura toda, mais itens).
- **Cor:** oito tons de vidro.
- **Exibição:** Simples (o essencial) ou Detalhada (resumo de cada item; nos e-mails, um trecho de cada mensagem).
- **Desfoque** do vidro do bloco.

O botão de engrenagem no topo ajusta o **desfoque do fundo** e conecta o Gmail. Tudo fica salvo no celular. Puxe a tela para baixo para atualizar.

O desfoque precisa de Android 12 ou mais novo; nos anteriores o vidro aparece sem desfoque.

## Instalar no celular

**https://github.com/dmwnezes/painel/releases/latest/download/Painel.apk**

O Android vai pedir para permitir a instalação de apps desta fonte. Aceite e instale. Novas versões instalam por cima da anterior.

## Gmail: configuração única no Google Cloud

O app lê os e-mails com a API do Gmail, que exige um projeto no Google Cloud ligado ao app:

1. Em https://console.cloud.google.com crie um projeto (ex.: "Painel").
2. Em **APIs e serviços › Biblioteca**, ative a **Gmail API**.
3. Em **Tela de permissão OAuth** (Google Auth Platform), escolha **Externo**, preencha nome e e-mail, e em **Público-alvo** adicione o seu Gmail como **usuário de teste**.
4. Em **Clientes** (ou Credenciais), crie um **ID do cliente OAuth** do tipo **Android** com:
   - Nome do pacote: `com.dmwnezes.painel`
   - SHA-1: `1B:F1:27:62:40:D3:19:69:92:89:38:7A:89:1C:1F:79:F2:B8:C4:28`
5. No app, toque em **Conectar Gmail** e autorize. O acesso é só de leitura.

Enquanto o projeto ficar em modo de teste, o Google pode pedir para reconectar de tempos em tempos.

## De onde vêm os dados

- Clima: Open-Meteo (sem chave).
- Notícias: RSS do g1, BBC Brasil, ge (Ceará) e O Povo; games pela busca do Google Notícias.
- Jogos do Fortaleza: API pública da ESPN (sem chave, não oficial; pode mudar sem aviso).
- E-mails: Gmail API, somente leitura.
- Agenda: agendas sincronizadas no próprio celular.

## Como é gerado

A cada push na `main`, o GitHub Actions roda os testes, gera o APK assinado e publica em **Releases**.
