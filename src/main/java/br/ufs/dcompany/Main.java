package br.ufs.dcompany;

import java.util.Locale;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int codigoSaida = 0;

        try {
            executar(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            mostrarAjuda();
            codigoSaida = 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Execução interrompida.");
            codigoSaida = 1;
        } catch (Exception e) {
            System.err.println("Falha na execução: " + e.getMessage());
            e.printStackTrace(System.err);
            codigoSaida = 1;
        }

        if (codigoSaida != 0) {
            System.exit(codigoSaida);
        }
    }

    private static void executar(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Informe o modo de execução.");
        }

        String modo = args[0].toLowerCase(Locale.ROOT);

        switch (modo) {
            case "--help", "-h", "ajuda" -> {
                validarArgumentos(args, 1, "--help");
                mostrarAjuda();
            }
            case "init" -> {
                validarArgumentos(args, 1, "init");
                Topologia.inicializar();
            }
            case "cliente" -> {
                validarArgumentos(args, 2, "cliente <pasta>");
                Cliente.executar(args[1]);
            }
            case "conversor" -> {
                validarArgumentos(args, 1, "conversor");
                Conversor.executar();
            }
            case "armazenamento" -> {
                validarArgumentos(args, 1, "armazenamento");
                Armazenamento.executar();
            }
            case "verificar" -> {
                validarArgumentos(args, 3,
                        "verificar <pasta-clientes> <pasta-armazenamento>");
                Validador.executar(args[1], args[2]);
            }
            default -> throw new IllegalArgumentException(
                    "Modo desconhecido: " + args[0]);
        }
    }

    private static void validarArgumentos(
            String[] args, int quantidade, String uso) {
        if (args.length != quantidade) {
            throw new IllegalArgumentException(
                    "Uso: java -jar target/dcompany.jar " + uso);
        }
    }

    private static void mostrarAjuda() {
        System.out.println("""
                DCompany - processamento de imagens com RabbitMQ

                Uso: java -jar target/dcompany.jar <modo> [argumentos]

                Modos:
                  init                       Cria as filas e o exchange.
                  cliente <pasta>            Envia as imagens dessa pasta.
                  conversor                  Aguarda e converte imagens.
                  armazenamento              Aguarda e salva imagens convertidas.
                  verificar <clientes> <armazenamento>
                                             Confere as imagens e as réplicas.
                  --help                     Mostra esta ajuda.

                Variáveis de ambiente (valores padrão):
                  RABBITMQ_HOST=localhost  RABBITMQ_PORT=5672
                  RABBITMQ_USER=dcompany  RABBITMQ_PASSWORD=dcompany_dev
                  RABBITMQ_VHOST=/
                  INSTANCE_ID=1           STORAGE_ID=1
                  STORAGE_IDS=1,2
                  STORAGE_DIR=./armazenamento/servidor<STORAGE_ID>

                Execute init antes de enviar imagens.
                Conversor e armazenamento ficam aguardando; Ctrl+C encerra.
                """);
    }
}