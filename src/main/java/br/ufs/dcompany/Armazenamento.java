package br.ufs.dcompany;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class Armazenamento {

    private Armazenamento() {
    }

    public static void executar() throws Exception {
        String storageId =
                RabbitMQConfig.env("STORAGE_ID", "1");

        String fila =
                Topologia.filaArmazenamento(storageId);

        String diretorio = RabbitMQConfig.env(
                "STORAGE_DIR",
                "./armazenamento/servidor" + storageId
        );

        Path pasta = Path.of(diretorio)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(pasta);

        CountDownLatch encerrar = new CountDownLatch(1);
        AtomicReference<Exception> erro = new AtomicReference<>();

        try (
                Connection conexao =
                        RabbitMQConfig.criarConexao(
                                "storage-" + storageId
                        );

                Channel canal = conexao.createChannel()
        ) {
            Topologia.declararArmazenamento(
                    canal,
                    storageId
            );

            canal.basicQos(1);

            DeliverCallback callback = (consumerTag, entrega) -> {
                long deliveryTag =
                        entrega.getEnvelope().getDeliveryTag();

                String nome = "(sem nome)";

                try {
                    Map<String, Object> headers =
                            entrega.getProperties().getHeaders();

                    if (headers == null
                            || headers.get("filename") == null) {

                        throw new IllegalArgumentException(
                                "Mensagem sem o header filename."
                        );
                    }

                    nome = headers.get("filename").toString();

                    Path destino = destinoSeguro(pasta, nome);

                    salvar(destino, entrega.getBody());

                    canal.basicAck(deliveryTag, false);

                    System.out.printf(
                            "[STORAGE %s] Salvo: %s | id=%s%n",
                            storageId,
                            destino,
                            entrega.getProperties().getMessageId()
                    );

                } catch (IllegalArgumentException e) {
                    System.err.printf(
                            "[STORAGE %s] Mensagem rejeitada: %s | %s%n",
                            storageId,
                            nome,
                            e.getMessage()
                    );

                    try {
                        canal.basicReject(deliveryTag, false);
                    } catch (IOException falhaRejeicao) {
                        erro.set(falhaRejeicao);
                        encerrar.countDown();
                    }

                } catch (Exception e) {
                    System.err.printf(
                            "[STORAGE %s] Falha ao salvar %s: %s%n",
                            storageId,
                            nome,
                            e.getMessage()
                    );

                    // Sem ACK: a mensagem volta para a fila
                    // quando esta conexão for fechada.
                    erro.set(e);
                    encerrar.countDown();
                }
            };

            canal.basicConsume(
                    fila,
                    false,
                    callback,
                    consumerTag -> {
                        erro.set(new IOException(
                                "O consumo da fila foi cancelado."
                        ));

                        encerrar.countDown();
                    }
            );

            System.out.printf(
                    "[STORAGE %s] Aguardando imagens... "
                            + "Fila: %s | Pasta: %s%n",
                    storageId,
                    fila,
                    pasta
            );

            encerrar.await();

            if (erro.get() != null) {
                throw erro.get();
            }
        }
    }

    private static Path destinoSeguro(Path pasta, String nome) {
        if (nome.isBlank()
                || nome.equals(".")
                || nome.equals("..")
                || nome.contains("/")
                || nome.contains("\\")) {

            throw new IllegalArgumentException(
                    "filename deve conter apenas o nome do arquivo."
            );
        }

        Path destino = pasta.resolve(nome).normalize();

        if (!pasta.equals(destino.getParent())) {
            throw new IllegalArgumentException(
                    "O arquivo deve ficar dentro da pasta de armazenamento."
            );
        }

        return destino;
    }

    private static void salvar(Path destino, byte[] dados)
            throws IOException {

        Path temporario = Files.createTempFile(
                destino.getParent(),
                ".recebendo-",
                ".tmp"
        );

        try {
            Files.write(temporario, dados);

            try {
                Files.move(
                        temporario,
                        destino,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );

            } catch (AtomicMoveNotSupportedException
                     | FileAlreadyExistsException e) {

                Files.move(
                        temporario,
                        destino,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

        } finally {
            Files.deleteIfExists(temporario);
        }
    }
}