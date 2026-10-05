package br.ufs.dcompany;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class Validador {

    private Validador() {
    }

    public static void executar(String diretorioClientes,
                               String diretorioArmazenamento)
            throws IOException {

        Path clientes = Path.of(diretorioClientes);
        Path armazenamento = Path.of(diretorioArmazenamento);

        Map<String, Path> originais = localizarOriginais(clientes);

        if (originais.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nenhuma imagem JPG ou PNG nas pastas dos clientes."
            );
        }

        Set<String> ids = new LinkedHashSet<>();

        for (String valor : RabbitMQConfig.env(
                "STORAGE_IDS", "1,2"
        ).split(",", -1)) {
            String id = valor.trim();
            Topologia.filaArmazenamento(id);
            ids.add(id);
        }

        Map<String, Path> primeirasCopias = new HashMap<>();

        for (String id : ids) {
            Path pasta = armazenamento.resolve("servidor" + id);
            Set<String> recebidas = new TreeSet<>();

            for (Path arquivo : listarArquivos(pasta)) {
                recebidas.add(arquivo.getFileName().toString());
            }

            Set<String> ausentes = new TreeSet<>(originais.keySet());
            ausentes.removeAll(recebidas);

            Set<String> extras = new TreeSet<>(recebidas);
            extras.removeAll(originais.keySet());

            if (!ausentes.isEmpty() || !extras.isEmpty()) {
                throw new IOException(
                        "servidor" + id + ": arquivos ausentes="
                                + ausentes + "; arquivos extras=" + extras
                );
            }

            for (Map.Entry<String, Path> entrada : originais.entrySet()) {
                String nome = entrada.getKey();
                Path copia = pasta.resolve(nome);

                BufferedImage original = lerImagem(entrada.getValue());
                BufferedImage convertida = lerImagem(copia);

                if (original.getWidth() != convertida.getWidth()
                        || original.getHeight() != convertida.getHeight()) {
                    throw new IOException(
                            "Dimensões diferentes do original: " + copia
                    );
                }

                verificarCinza(convertida, copia);

                Path referencia = primeirasCopias.putIfAbsent(nome, copia);

                if (referencia != null
                        && Files.mismatch(referencia, copia) != -1) {
                    throw new IOException(
                            "Réplicas diferentes: " + referencia
                                    + " e " + copia
                    );
                }
            }

            System.out.printf(
                    "[VALIDAÇÃO] servidor%s: %d imagens verificadas.%n",
                    id,
                    recebidas.size()
            );
        }

        System.out.printf(
                "[VALIDAÇÃO] SUCESSO: %d imagens em %d servidores. "
                        + "Nomes e dimensões preservados; "
                        + "todas em cinza; réplicas idênticas.%n",
                originais.size(),
                ids.size()
        );
    }

    private static Map<String, Path> localizarOriginais(Path raiz)
            throws IOException {

        if (!Files.isDirectory(raiz)) {
            throw new IOException(
                    "Pasta de clientes não encontrada: " + raiz
            );
        }

        List<Path> pastas;

        try (Stream<Path> lista = Files.list(raiz)) {
            pastas = lista
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        }

        Map<String, Path> originais = new TreeMap<>();
        Set<String> nomesNormalizados = new TreeSet<>();

        for (Path pasta : pastas) {
            for (Path arquivo : listarArquivos(pasta)) {
                String nome = arquivo.getFileName().toString();
                String minusculo = nome.toLowerCase(Locale.ROOT);

                if (!minusculo.endsWith(".jpg")
                        && !minusculo.endsWith(".jpeg")
                        && !minusculo.endsWith(".png")) {
                    continue;
                }

                if (!nomesNormalizados.add(minusculo)) {
                    throw new IllegalArgumentException(
                            "Nome repetido entre os clientes: " + nome
                                    + ". Use nomes únicos, inclusive "
                                    + "sem depender de maiúsculas/minúsculas."
                    );
                }

                originais.put(nome, arquivo);
            }
        }

        return originais;
    }

    private static List<Path> listarArquivos(Path pasta)
            throws IOException {

        if (!Files.isDirectory(pasta)) {
            throw new IOException(
                    "Pasta não encontrada: " + pasta
            );
        }

        try (Stream<Path> lista = Files.list(pasta)) {
            return lista
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
    }

    private static BufferedImage lerImagem(Path arquivo)
            throws IOException {

        BufferedImage imagem = ImageIO.read(arquivo.toFile());

        if (imagem == null) {
            throw new IOException(
                    "Imagem inválida: " + arquivo
            );
        }

        return imagem;
    }

    private static void verificarCinza(
            BufferedImage imagem,
            Path arquivo
    ) throws IOException {

        for (int y = 0; y < imagem.getHeight(); y++) {
            for (int x = 0; x < imagem.getWidth(); x++) {
                int pixel = imagem.getRGB(x, y);

                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                if (r != g || g != b) {
                    throw new IOException(
                            "Imagem ainda colorida: " + arquivo
                                    + " no pixel (" + x + ", " + y + ")"
                    );
                }
            }
        }
    }
}