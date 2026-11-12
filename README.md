# Rota Livre

App Android **gratuito e de distribuição livre** que lê as ofertas de corrida da **Uber**, da **99** e de apps de **delivery** (iFood, Loggi, Shopee Entregador) e mostra, em um quadro por cima da tela, se a corrida compensa.

O app **só mostra informação**: não aceita nem recusa corridas, não coleta dados e não envia nada pela internet.

## O que o quadro mostra
- **Valor por km** (preço ÷ distância total: busca + viagem) em destaque, com o selo **BOA CORRIDA**, **ABAIXO DO MÍNIMO** ou **PASSAGEIRO LONGE**
- Distância até o passageiro (ou até a coleta, no delivery)
- Valor por hora estimado
- Lucro da corrida, descontando combustível e manutenção (opcional)
- Modo **pico**, com mínimo próprio quando a oferta é dinâmica
- Resumo do dia (ofertas e média R$/km)

O quadro aparece assim que a oferta chega e some sozinho depois de 12 segundos.

## Ajustes no app
- Mínimo de R$/km **por dia da semana**
- Mínimo no pico e distância máxima até o passageiro
- Delivery com regras próprias (mínimo por entrega e distância máxima até a coleta)
- Custos do carro: gasolina, etanol ou GNV, preço, consumo e manutenção por km
- **Tamanho da janela** (80% a 180%), com botão para ver um exemplo antes de salvar
- Escolher o que aparece no quadro
- Tema claro e escuro

## Instalação
1. **[Baixar o APK](https://andreluizs.com.br/rotalivre)** (ou veja todas as versões em [Releases](../../releases)) e instale (permita "fontes desconhecidas").
2. Abra o Rota Livre e toque em **Ativar** → ligue **Rota Livre** na Acessibilidade.
3. Ajuste os valores e toque em **Salvar**.
4. Abra o app da Uber, da 99 ou de delivery e fique online.

> **Por que Acessibilidade?** É a única forma de o Android permitir ler o texto da oferta na tela.
> O app lê apenas os apps listados acima, processa tudo no próprio aparelho e não tem permissão de internet.
>
> Se o quadro não aparecer depois de atualizar o app, desligue e ligue o Rota Livre em Configurações → Acessibilidade.

## Apoie (opcional)
O app é e continua gratuito. Se ele ajuda você, um Pix de qualquer valor é bem-vindo: o QR Code e o "copia e cola" estão no fim da tela do app.

## Para desenvolvedores
Requisitos: JDK 17+ e Android SDK 34.

```bash
./gradlew testDebugUnitTest   # testes do leitor de ofertas
./gradlew assembleDebug       # APK em app/build/outputs/apk/debug/
```

Para gerar o APK de release assinado, crie uma keystore e o arquivo `~/.rotalivre/keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), e rode `./gradlew assembleRelease`.

### Publicar uma nova versão
Crie uma release no GitHub e anexe o APK assinado com o nome **`RotaLivre.apk`**.
O link `andreluizs.com.br/rotalivre` sempre baixa o `RotaLivre.apk` da release mais recente.

### Estrutura
| Arquivo | Função |
|---|---|
| `RideService.kt` | Serviço de Acessibilidade: lê a tela, calcula e desenha o quadro |
| `OfferParser.kt` | Extrai preço, distâncias e tempo do texto da oferta (com testes) |
| `Targets.kt` | Pacotes dos apps lidos (repita em `accessibility_config.xml`) |
| `Prefs.kt` / `Stats.kt` | Configurações e resumo do dia |
| `MainActivity.kt` | Tela de configuração |

### Adicionar um app de delivery
Inclua o pacote em `Targets.kt` e em `res/xml/accessibility_config.xml` (`packageNames`).
Capture o texto da oferta na versão debug (`adb logcat -s RotaLivre`) e, se preciso, ajuste as regex de `OfferParser.kt` e adicione um teste.

## Aviso
"Uber", "99", "iFood", "Loggi" e "Shopee" são marcas de seus donos. O Rota Livre não tem vínculo com essas empresas.

Criado por André Luiz — https://andreluizs.com.br
