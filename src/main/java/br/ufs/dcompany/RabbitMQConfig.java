package br.ufs.dcompany;

import com.rabbitmq.client.AuthenticationFailureException;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public final class RabbitMQConfig {

    private RabbitMQConfig() {
    }

    public static String env(String nome, String valorPadrao) {
        String valor = System.getenv(nome);

        if (valor == null || valor.isBlank()) {
            return valorPadrao;
        }

        return valor;
    }

    public static Connection criarConexao(String nomeInstancia)
            throws IOException, InterruptedException {

        ConnectionFactory factory = new ConnectionFactory();

        factory.setHost(env("RABBITMQ_HOST", "localhost"));
        factory.setPort(
                Integer.parseInt(env("RABBITMQ_PORT", "5672"))
        );

        factory.setUsername(env("RABBITMQ_USER", "dcompany"));
        factory.setPassword(env("RABBITMQ_PASSWORD", "dcompany_dev"));
        factory.setVirtualHost(env("RABBITMQ_VHOST", "/"));

        factory.setConnectionTimeout(10_000);
        factory.setRequestedHeartbeat(30);

        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5_000);

        int maxTentativas = 12;

        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {

            try {
                Connection conexao =
                        factory.newConnection(nomeInstancia);

                System.out.printf(
                        "[RABBITMQ] Conectado: %s%n",
                        nomeInstancia
                );

                return conexao;

            } catch (AuthenticationFailureException e) {
                throw new IOException(
                        "RabbitMQ recusou o login. Confira usuário e senha.",
                        e
                );

            } catch (IOException | TimeoutException e) {
                if (tentativa == maxTentativas) {
                    throw new IOException(
                            "Não foi possível conectar ao RabbitMQ após "
                                    + maxTentativas + " tentativas.",
                            e
                    );
                }

                System.err.printf(
                        "[RABBITMQ] Tentativa %d/%d falhou. "
                                + "Tentando novamente em 3 segundos.%n",
                        tentativa,
                        maxTentativas
                );

                Thread.sleep(3_000);
            }
        }

        throw new IOException("Não foi possível criar a conexão.");
    }
}