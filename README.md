# DCompany — processamento de imagens com RabbitMQ

Sistema da atividade de Sistemas Distribuídos, implementado em Java. Dois clientes enviam imagens para uma fila compartilhada. Dois conversores consomem essa fila e transformam as imagens em tons de cinza. Cada imagem convertida é entregue aos dois servidores de armazenamento, com seu nome original.

## Stack

- Java, com compilação configurada para release 17.
- Maven para compilar e empacotar o projeto.
- Cliente Java do RabbitMQ, protocolo AMQP 0-9-1.
- ImageIO e BufferedImage para leitura, conversão e gravação das imagens.
- RabbitMQ com plugin Management.
- Docker e Docker Compose para executar as instâncias.

O Dockerfile compila o projeto com Maven e Java 17 em uma etapa e copia o JAR para outra etapa, que usa o runtime Java 17. O comando Maven não precisa estar instalado no Windows para esse fluxo com Docker.

## Organização do projeto

| Caminho relativo à raiz | Função |
| --- | --- |
| `pom.xml` | Dependências, compilação e geração do JAR com dependências. |
| `Dockerfile` | Construção da imagem da aplicação. |
| `.dockerignore` | Arquivos excluídos do contexto de construção. |
| `compose.yaml` | Serviços, dependências, rede e volumes. |
| `src/main/java/br/ufs/dcompany/` | Classes Java da aplicação. |
| `clientes/cliente1/` | Imagens originais do cliente 1. |
| `clientes/cliente2/` | Imagens originais do cliente 2. |
| `armazenamento/servidor1/` | Imagens convertidas salvas pelo armazenamento 1. |
| `armazenamento/servidor2/` | Imagens convertidas salvas pelo armazenamento 2. |

As imagens devem estar diretamente nas pastas dos clientes. Os formatos aceitos são JPG, JPEG e PNG. Use nomes diferentes entre os clientes, pois as saídas preservam o nome original e compartilham cada pasta de armazenamento.

## Mensageria

| Recurso | Nome | Comportamento |
| --- | --- | --- |
| Fila de entrada | `dcompany.imagens` | Compartilhada pelos conversores, que competem pelas mensagens. |
| Exchange de saída | `dcompany.imagens.convertidas` | Tipo fanout: encaminha cada imagem convertida a todas as filas vinculadas. |
| Fila do armazenamento 1 | `dcompany.storage.1` | Recebe todas as imagens convertidas. |
| Fila do armazenamento 2 | `dcompany.storage.2` | Recebe todas as imagens convertidas. |

O modo `init` declara as filas e os vínculos antes de iniciar os conversores e armazenamentos. As filas e o exchange são duráveis. As publicações usam mensagens persistentes e confirmações do broker. O conversor confirma a entrada após publicar a saída com sucesso; o armazenamento confirma a mensagem após salvar o arquivo.

Esse fluxo pode produzir reentregas quando ocorre uma falha antes de uma confirmação. A gravação usa o nome original e substitui o arquivo de mesmo nome. Os nomes diferentes entre clientes evitam que imagens distintas substituam umas às outras.

## Execução no Windows

Abra o Docker Desktop e execute os comandos no PowerShell, dentro da raiz do projeto:

```powershell
cd C:\Users\Joseph\downloads\rabbitmq
docker compose config --quiet
```

Se a validação não apresentar erro, construa a imagem e inicie os serviços:

```powershell
docker build -t dcompany:1.0 .
docker run --rm dcompany:1.0 --help
docker compose up -d
docker compose ps -a
```

O RabbitMQ deve aparecer como `Up (healthy)`. Os conversores e armazenamentos ficam em execução. O serviço `init` termina com `Exited (0)` após criar as filas.

O painel do RabbitMQ fica em <http://localhost:15672>. As credenciais configuradas para a atividade são usuário `dcompany` e senha `dcompany_dev`.

## Envio e validação

Coloque uma ou mais imagens coloridas em cada pasta de cliente. No teste realizado, foram usados `foto1.png` no cliente 1 e `foto2.png` no cliente 2.

Inicie os dois clientes:

```powershell
docker compose up -d cliente1 cliente2
```

Cada cliente envia suas imagens e encerra. Para enviar novamente os arquivos presentes nas pastas, execute esse mesmo comando novamente.

Confira o processamento:

```powershell
docker compose logs --tail=30 cliente1 cliente2 conversor1 conversor2 armazenamento1 armazenamento2
```

Depois que os dois armazenamentos registrarem todos os arquivos salvos, execute:

```powershell
docker compose run --rm verificar
```

O verificador confere a lista de arquivos, os nomes originais, as dimensões, os tons de cinza e a igualdade dos bytes das réplicas. Ele retorna código de saída 0 quando a validação passa e código diferente de zero quando encontra um problema.

Os clientes usam o perfil `envio` e o verificador usa `verificacao`. Eles são ativados quando seus serviços são explicitamente selecionados nos comandos acima.

## Resultado do teste realizado

Os registros fornecidos confirmaram este fluxo:

| Arquivo | Cliente de origem | Conversor | Armazenamentos que salvaram |
| --- | --- | --- | --- |
| `foto1.png` | Cliente 1 | Conversor 2 | Servidor 1 e servidor 2. |
| `foto2.png` | Cliente 2 | Conversor 1 | Servidor 1 e servidor 2. |

O verificador foi executado duas vezes e informou sucesso nas duas execuções:

```text
[VALIDAÇÃO] servidor1: 2 imagens verificadas.
[VALIDAÇÃO] servidor2: 2 imagens verificadas.
[VALIDAÇÃO] SUCESSO: 2 imagens em 2 servidores. Nomes e dimensões preservados; todas em cinza; réplicas idênticas.
```

O teste demonstra dois clientes enviando imagens, divisão do processamento entre dois conversores e armazenamento de ambas as imagens nos dois servidores, com nomes e dimensões preservados e cópias idênticas.

## Conferência dos requisitos e das evidências

Esta tabela relaciona os itens do enunciado às evidências registradas na execução. O teste documentado usa duas imagens PNG, dois clientes, dois conversores e dois armazenamentos.

| Item do enunciado | Evidência registrada | Situação |
| --- | --- | --- |
| Comunicação usando RabbitMQ | Broker `Up (healthy)` e registros de conexão dos clientes, conversores e armazenamentos. A seção de mensageria identifica as filas e o exchange usados. | Demonstrado. |
| Vários clientes produtores | Cliente 1 confirmou o envio de `foto1.png`; cliente 2 confirmou o envio de `foto2.png`. | Demonstrado com dois clientes. |
| Vários servidores de conversão | Conversor 2 processou `foto1.png`; conversor 1 processou `foto2.png`. | Demonstrado com dois conversores. |
| Conversão das imagens para tons de cinza | O verificador informou que todas as imagens armazenadas estavam em cinza. | Demonstrado para as duas imagens PNG do teste. |
| Todos os armazenamentos recebem todas as imagens | Cada armazenamento registrou as duas gravações; o verificador encontrou duas imagens em cada servidor e confirmou réplicas idênticas. | Demonstrado com dois armazenamentos. |
| Preservar o nome original | As saídas foram salvas como `foto1.png` e `foto2.png`; o verificador confirmou os nomes preservados. | Demonstrado. |
| Programa minimamente testável e instâncias executáveis | Modos de execução separados, instâncias no Compose, registros de processamento e comando `verificar` executado com sucesso. | Demonstrado. |
| Docker e arquivos de configuração para automatizar a execução e os testes | Construção da imagem concluída, execução do JAR no contêiner, validação do Compose e inicialização dos serviços. | Demonstrado; configurações em `Dockerfile` e `compose.yaml`. |
| Comandos necessários para rodar a aplicação | Comandos de construção, inicialização, envio, consulta dos registros e validação documentados neste README e usados no teste. | Documentado e executado. |
| Pastas separadas, conforme a organização sugerida | Pastas dos clientes e dos armazenamentos montadas como volumes; as imagens de ambos os clientes foram verificadas em ambos os armazenamentos. | Organização adotada. |
| Reduzir o espaço ocupado pelas imagens, objetivo descrito no problema | Os registros mostram os tamanhos enviados, mas não os tamanhos dos arquivos convertidos. | Comparação de tamanho ainda não registrada. |

O README documenta a execução. A checagem automática dos nomes, dimensões, tons de cinza e igualdade das réplicas é feita pelo modo `verificar`.

## Comparação de tamanho dos arquivos

Para medir o tamanho antes e depois da conversão, execute este comando no PowerShell, dentro da raiz do projeto, após a validação ter passado:

```powershell
Get-ChildItem -LiteralPath .\clientes -File -Recurse |
    Where-Object { $_.Extension -in '.jpg', '.jpeg', '.png' } |
    ForEach-Object {
        $imagemOriginal = $_
        $imagemCinza = Get-Item -LiteralPath (
            Join-Path -Path '.\armazenamento\servidor1' -ChildPath $imagemOriginal.Name
        ) -ErrorAction Stop

        [pscustomobject]@{
            Arquivo = $imagemOriginal.Name
            OriginalBytes = $imagemOriginal.Length
            CinzaBytes = $imagemCinza.Length
            ReducaoPercentual = [Math]::Round(
                100 * ($imagemOriginal.Length - $imagemCinza.Length) / $imagemOriginal.Length,
                2
            )
        }
    } | Format-Table -AutoSize
```

O campo `ReducaoPercentual` é positivo quando o arquivo convertido é menor, zero quando os tamanhos são iguais e negativo quando o arquivo convertido é maior. A comparação usa o servidor 1; a igualdade das réplicas já foi confirmada pelo verificador. Registre a saída desse comando junto das evidências do teste para documentar também esse objetivo.

## Referências

- [Ordem de inicialização no Docker Compose](https://docs.docker.com/compose/how-tos/startup-order/)
- [Perfis do Docker Compose](https://docs.docker.com/compose/how-tos/profiles/)
- [Construção Docker em múltiplas etapas](https://docs.docker.com/build/building/multi-stage/)
