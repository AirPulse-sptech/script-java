package school.sptech;

import java.util.Optional;
import school.sptech.config.Jira;
import school.sptech.config.S3Provider;
import school.sptech.dto.LeituraDto;
import school.sptech.dto.LeiturasGoldDto;
import school.sptech.model.Componente;
import school.sptech.model.Nivel;
import school.sptech.repository.ComponenteRepository;
import school.sptech.service.GoldReader;
import school.sptech.service.VerificadorLimites;

public class Main {

    public static void main(String[] args) throws Exception {
        // para mudar as váriaveis de ambiente, clique no triângulo verde a esquerda de public static void main
        // "modify run configuration" e em enviroment variables clique no simbolo de página
        String bucket = obrigatoria("BUCKET_GOLD");
        String chave = System.getenv().getOrDefault("GOLD_KEY", "leituras_componentes.json");
        String projetoJira = obrigatoria("JIRA_PROJECT_KEY");
        String tipoIssue = System.getenv().getOrDefault("JIRA_ISSUE_TYPE", "Task");

        Jira jira = new Jira(obrigatoria("JIRA_BASE_URL"), obrigatoria("JIRA_EMAIL"), obrigatoria("JIRA_API_TOKEN"));

        System.out.println("Lendo s3://" + bucket + "/" + chave);
        LeiturasGoldDto gold = new GoldReader(new S3Provider().getS3Client()).ler(bucket, chave);
        System.out.println("Leituras recebidas: " + gold.getLeituras().size() + " (gerado em " + gold.getGeradoEm() + ")");

        int alertas = 0;

        try (ComponenteRepository repository = new ComponenteRepository(
              obrigatoria("DB_URL"), obrigatoria("DB_USER"), obrigatoria("DB_PASSWORD"))) {

            for (LeituraDto leitura : gold.getLeituras()) {
                Optional<Componente> encontrado = repository.buscarPorId(leitura.getIdComponente());

                if (encontrado.isEmpty()) {
                    System.out.println("[AVISO] Componente " + leitura.getIdComponente() + " não existe no banco. Ignorado.");
                    continue;
                }

                Componente componente = encontrado.get();
                Nivel nivel = VerificadorLimites.classificar(leitura.getValor(), componente);

                System.out.printf("%-8s %s | %s | valor %.1f | atenção %.1f | crítico %.1f%n",
                      nivel, componente.aeronave(), componente.tipo(), leitura.getValor(),
                      componente.limiteAtencao(), componente.limiteCritico());

                if (nivel != Nivel.NORMAL) {
                    String resumo = montarResumo(nivel, componente, leitura.getValor());
                    String resposta = jira.createIssue(projetoJira, resumo, tipoIssue);
                    System.out.println("   -> issue criada no Jira: " + resposta);
                    alertas++;
                }
            }
        }

        System.out.println("Concluído. Alertas enviados ao Jira: " + alertas);
    }

    private static String montarResumo(Nivel nivel, Componente c, double valor) {
        String rotulo = nivel == Nivel.CRITICO ? "CRÍTICO" : "ATENÇÃO";
        double limite = nivel == Nivel.CRITICO ? c.limiteCritico() : c.limiteAtencao();
        String unidade = c.unidade() == null ? "" : " " + c.unidade();
        return String.format("[%s] %s - %s (%s): %.1f%s, limite %.1f%s",
              rotulo, c.aeronave(), c.tipo(), c.modelo(), valor, unidade, limite, unidade);
    }

    private static String obrigatoria(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente não definida: " + nome);
        }
        return valor;
    }
}
