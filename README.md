# Rota Livre

App Android **gratuito e de distribuição livre** que lê as ofertas de corrida da Uber e da 99 na tela e mostra por cima:
- distância até o passageiro (com alerta se passar do máximo)
- valor por km (preço ÷ distância total: busca + viagem)
- valor por hora estimado
- modo PICO (botão manual; quando ligado, detecta "dinâmico", "pico", multiplicador ou valor extra na oferta)
- resumo do dia (quantidade de ofertas e média R$/km)
- lucro da corrida já descontando combustível e manutenção (opcional)

Quadro **verde** = corrida boa (critério: valor por km); **vermelho** = abaixo do mínimo ou passageiro longe.
O app só mostra informação, não aceita nem recusa corridas.

Criado por André Luiz — https://andreluizs.com.br

## Painel
- Tabela com o mínimo R$/km por dia da semana e faixa de horário (0-6h, 6-12h, 12-18h, 18-24h)
- Mínimo R$/km no pico
- Distância máxima até o passageiro
- Custos do carro (opcional): combustível (gasolina, etanol ou GNV), preço, consumo e manutenção por km
- Caixinhas para escolher o que aparece no quadro: distância até o passageiro, valor por km, valor por hora, lucro e resumo do dia
- Apoie o desenvolvedor (opcional): QR Code Pix e botões para copiar o Pix copia e cola ou a chave
- Tela "Sobre" com créditos e link do site

## Apoio via Pix
- Após 2 dias do primeiro uso, aparece uma janelinha convidando a apoiar (valor livre).
- "Agora não" → volta depois de 10 dias. "Já apoiei" → nunca mais aparece.
- O apoio fica sempre disponível no fim do painel.
- Chave e código Pix ficam em `Support.kt`; o QR Code em `res/drawable-nodpi/pix_qr.jpg`.

## Para o Claude Code (VS Code)
1. Gerar o Gradle Wrapper (`gradle wrapper --gradle-version 8.7`) ou abrir no Android Studio, que gera sozinho.
2. Compilar: `./gradlew assembleDebug`
3. APK sai em `app/build/outputs/apk/debug/app-debug.apk`
4. Requisitos: JDK 17 e Android SDK 34.
5. Para distribuir para outras pessoas, gerar um APK de release assinado (`./gradlew assembleRelease` com uma keystore própria).

## No celular
1. Instalar o APK (permitir "fontes desconhecidas").
2. Abrir o Rota Livre → "Ativar leitura" → ligar "Rota Livre" na Acessibilidade.
3. Preencher os valores e tocar em Salvar.

## Atenção
- Pacote da 99 configurado como `com.app99.driver`. Confirmar no celular
  (Configurações → Apps → 99 Motorista) e, se for diferente, trocar em
  `RideService.kt`, `accessibility_config.xml` e `AndroidManifest.xml`.
- Se algum valor sair errado, ajustar as regex em `OfferParser.kt`.
- "Uber" e "99" são marcas de seus donos; o Rota Livre não tem vínculo com essas empresas.
