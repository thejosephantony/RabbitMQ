package br.ufs.dcompany;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class Conversor {

    private Conversor() {
    }

    public static void executar() throws Exception {
        String conversorId =
                RabbitMQConfig.env("INSTANCE_ID", "1");

        CountDownLatch encerrar = new CountDownLatch(1);
        AtomicReference<Exception> erro = new AtomicReference<>();
        AtomicBoolean devolvida = new AtomicBoolean(false);

        try (
                Connection conexao =
                        RabbitMQConfig.criarConexao(
                                "conversor-" + conversorId
                        );

                Channel consumo = conexao.createChannel();
                Channel publicacao = conexao.createChannel()
        ) {
            Topologia.declararBase(consumo);

            consumo.basicQos(1);
            publicacao.confirmSelect();

            publicacao.addReturnListener(
                    (codigo, texto, exchange, routingKey,
                     propriedades, dados) -> devolvida.set(true)
            );

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

                    System.out.printf(
                            "[CONVERSOR %s] Convertendo: %s%n",
                            conversorId,
                            nome
                    );

                    byte[] convertida =
                            converter(entrega.getBody(), nome);

                    Map<String, Object> novosHeaders =
                            new HashMap<>(headers);

                    novosHeaders.put("converterId", conversorId);

                    AMQP.BasicProperties propriedades =
                            entrega.getProperties()
                                    .builder()
                                    .deliveryMode(2)
                                    .headers(novosHeaders)
                                    .build();

                    devolvida.set(false);

                    publicacao.basicPublish(
                            Topologia.EXCHANGE_CONVERTIDAS,
                            "",
                            true,
                            propriedades,
                            convertida
                    );

                    publicacao.waitForConfirmsOrDie(10_000);

                    if (devolvida.get()) {
                        throw new IOException(
                                "Resultado sem fila de destino. "
                                        + "Execute a inicialização das filas."
                        );
                    }

                    consumo.basicAck(deliveryTag, false);

                    System.out.printf(
                            "[CONVERSOR %s] Concluído: %s "
                                    + "| id=%s%n",
                            conversorId,
                            nome,
                            propriedades.getMessageId()
                    );

                } catch (IllegalArgumentException e) {
                    System.err.printf(
                            "[CONVERSOR %s] Imagem rejeitada: %s | %s%n",
                            conversorId,
                            nome,
                            e.getMessage()
                    );

                    try {
                        consumo.basicReject(deliveryTag, false);
                    } catch (IOException falhaRejeicao) {
                        erro.set(falhaRejeicao);
                        encerrar.countDown();
                    }

                } catch (Exception e) {
                    System.err.printf(
                            "[CONVERSOR %s] Falha ao processar %s: %s%n",
                            conversorId,
                            nome,
                            e.getMessage()
                    );

                    // Sem ACK: a mensagem será reentregue
                    // quando esta conexão for fechada.
                    erro.set(e);
                    encerrar.countDown();
                }
            };

            consumo.basicConsume(
                    Topologia.FILA_IMAGENS,
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
                    "[CONVERSOR %s] Aguardando imagens...%n",
                    conversorId
            );

            encerrar.await();

            if (erro.get() != null) {
                throw erro.get();
            }
        }
    }

    private static byte[] converter(byte[] dados, String nome) {
        String extensao = nome.substring(
                nome.lastIndexOf('.') + 1
        ).toLowerCase(Locale.ROOT);

        String formato = switch (extensao) {
            case "jpg", "jpeg" -> "jpg";
            case "png" -> "png";
            default -> throw new IllegalArgumentException(
                    "Formato não suportado: " + extensao
            );
        };

        try {
            BufferedImage original = ImageIO.read(
                    new ByteArrayInputStream(dados)
            );

            if (original == null) {
                throw new IllegalArgumentException(
                        "Os bytes recebidos não representam uma imagem válida."
                );
            }

            BufferedImage cinza = new BufferedImage(
                    original.getWidth(),
                    original.getHeight(),
                    BufferedImage.TYPE_BYTE_GRAY
            );

            Graphics2D graphics = cinza.createGraphics();

            try {
                // Fundo branco para imagens com transparência.
                graphics.setColor(Color.WHITE);
                graphics.fillRect(
                        0, 0,
                        cinza.getWidth(),
                        cinza.getHeight()
                );

                graphics.drawImage(original, 0, 0, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream saida =
                    new ByteArrayOutputStream();

            if (!ImageIO.write(cinza, formato, saida)) {
                throw new IllegalArgumentException(
                        "Não foi possível codificar a imagem como " + formato
                );
            }

            return saida.toByteArray();

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Falha ao ler ou converter a imagem.",
                    e
            );
        }
    }
}