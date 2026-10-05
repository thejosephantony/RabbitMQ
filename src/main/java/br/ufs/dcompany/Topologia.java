package br.ufs.dcompany;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Topologia {

    public static final String FILA_IMAGENS =
            "dcompany.imagens";

    public static final String EXCHANGE_CONVERTIDAS =
            "dcompany.imagens.convertidas";

    private Topologia() {
    }

    public static String filaArmazenamento(String storageId) {
        if (storageId == null
                || !storageId.matches("[A-Za-z0-9_-]+")) {

            throw new IllegalArgumentException(
                    "STORAGE_ID deve conter apenas letras, "
                            + "números, hífen ou underscore."
            );
        }

        return "dcompany.storage." + storageId;
    }

    public static void declararBase(Channel canal)
            throws IOException {

        canal.queueDeclare(
                FILA_IMAGENS,
                true,
                false,
                false,
                null
        );

        canal.exchangeDeclare(
                EXCHANGE_CONVERTIDAS,
                "fanout",
                true
        );
    }

    public static void declararArmazenamento(
            Channel canal,
            String storageId
    ) throws IOException {

        String fila = filaArmazenamento(storageId);

        declararBase(canal);

        canal.queueDeclare(
                fila,
                true,
                false,
                false,
                null
        );

        canal.queueBind(
                fila,
                EXCHANGE_CONVERTIDAS,
                ""
        );
    }

    public static void inicializar() throws Exception {
        String configuracao =
                RabbitMQConfig.env("STORAGE_IDS", "1,2");

        Set<String> ids = new LinkedHashSet<>();

        for (String valor : configuracao.split(",", -1)) {
            String id = valor.trim();

            // Valida todos os IDs antes de conectar.
            filaArmazenamento(id);
            ids.add(id);
        }

        try (
                Connection conexao =
                        RabbitMQConfig.criarConexao("init");

                Channel canal = conexao.createChannel()
        ) {
            declararBase(canal);

            for (String id : ids) {
                declararArmazenamento(canal, id);

                System.out.printf(
                        "[TOPOLOGIA] Fila de armazenamento pronta: %s%n",
                        filaArmazenamento(id)
                );
            }

            System.out.println(
                    "[TOPOLOGIA] Inicialização concluída."
            );
        }
    }
}