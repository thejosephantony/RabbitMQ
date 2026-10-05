package br.ufs.dcompany;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

public final class Cliente {

    private Cliente() {
    }

    public static void executar(String diretorio) throws Exception {
        Path pasta = Path.of(diretorio);

        if (!Files.isDirectory(pasta)) {
            throw new IllegalArgumentException(
                    "Pasta de imagens não encontrada: " + pasta
            );
        }

        List<Path> arquivos;

        try (Stream<Path> lista = Files.list(pasta)) {
            arquivos = lista
                    .filter(Files::isRegularFile)
                    .filter(Cliente::formatoSuportado)
                    .sorted()
                    .toList();
        }

        String clienteId =
                RabbitMQConfig.env("INSTANCE_ID", "1");

        if (arquivos.isEmpty()) {
            System.out.printf(
                    "[CLIENTE %s] Nenhuma imagem JPG ou PNG em %s%n",
                    clienteId,
                    pasta
            );

            return;
        }

        try (
                Connection conexao =
                        RabbitMQConfig.criarConexao(
                                "cliente-" + clienteId
                        );

                Channel canal = conexao.createChannel()
        ) {
            Topologia.declararBase(canal);
            canal.confirmSelect();

            for (Path arquivo : arquivos) {
                String nome =
                        arquivo.getFileName().toString();

                byte[] dados = Files.readAllBytes(arquivo);

                if (ImageIO.read(
                        new ByteArrayInputStream(dados)
                ) == null) {

                    throw new IOException(
                            "Imagem inválida ou não reconhecida: " + nome
                    );
                }

                String messageId =
                        UUID.randomUUID().toString();

                AMQP.BasicProperties propriedades =
                        new AMQP.BasicProperties.Builder()
                                .deliveryMode(2)
                                .messageId(messageId)
                                .headers(java.util.Map.of(
                                        "filename", nome,
                                        "clientId", clienteId
                                ))
                                .build();

                canal.basicPublish(
                        "",
                        Topologia.FILA_IMAGENS,
                        propriedades,
                        dados
                );

                canal.waitForConfirmsOrDie(10_000);

                System.out.printf(
                        "[CLIENTE %s] Envio confirmado: %s "
                                + "(%d bytes) | id=%s%n",
                        clienteId,
                        nome,
                        dados.length,
                        messageId
                );
            }

            System.out.printf(
                    "[CLIENTE %s] Concluído: %d imagens enviadas.%n",
                    clienteId,
                    arquivos.size()
            );
        }
    }

    private static boolean formatoSuportado(Path arquivo) {
        String nome = arquivo.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        return nome.endsWith(".jpg")
                || nome.endsWith(".jpeg")
                || nome.endsWith(".png");
    }
}