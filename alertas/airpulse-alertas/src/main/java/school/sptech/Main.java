package school.sptech;

import java.util.List;
import java.util.Optional;
import school.sptech.config.Jira;
import school.sptech.config.S3Provider;
import school.sptech.dto.AlertaDto;
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
        List<AlertaDto> eventos = new GoldReader(new S3Provider().getS3Client()).ler(bucket, chave);
        System.out.println("Eventos recebidos: " + eventos.size());

        int alertas = 0;

        try (ComponenteRepository repository = new ComponenteRepository(
                obrigatoria("DB_URL"), obrigatoria("DB_USER"), obrigatoria("DB_PASSWORD"))) {

            for (AlertaDto evento : eventos) {
                if (evento.componenteId() == null || evento.valor() == null) {
                    System.out.println("[AVISO] Evento sem componente_id ou valor. Ignorado.");
                    continue;
                }

                Optional<Componente> encontrado = repository.buscarPorId(evento.componenteId());

                if (encontrado.isEmpty()) {
                    System.out.println("[AVISO] Componente " + evento.componenteId() + " não existe no banco. Ignorado.");
                    continue;
                }

                Componente componente = encontrado.get();
                Nivel nivel = VerificadorLimites.classificar(evento.valor(), componente);

                System.out.printf("%-8s %s | %s | valor %.1f | atenção %.1f | crítico %.1f%n",
                        nivel, componente.aeronave(), componente.tipo(), evento.valor(),
                        componente.limiteAtencao(), componente.limiteCritico());

                if (nivel != Nivel.NORMAL) {
                    String resumo = montarResumo(nivel, componente, evento.valor(), evento.fase());
                    String resposta = jira.createIssue(projetoJira, resumo, tipoIssue);
                    System.out.println("   -> issue criada no Jira: " + resposta);
                    alertas++;
                }
            }
        }

        System.out.println("Concluído. Alertas enviados ao Jira: " + alertas);
    }

    private static String montarResumo(Nivel nivel, Componente c, double valor, String fase) {
        String rotulo = nivel == Nivel.CRITICO ? "CRÍTICO" : "ATENÇÃO";
        double limite = nivel == Nivel.CRITICO ? c.limiteCritico() : c.limiteAtencao();
        String unidade = c.unidade() == null ? "" : " " + c.unidade();
        String faseTxt = fase == null ? "" : " | fase " + fase;
        return String.format("[%s] %s - %s (%s): %.1f%s, limite %.1f%s%s",
                rotulo, c.aeronave(), c.tipo(), c.modelo(), valor, unidade, limite, unidade, faseTxt);
    }

    private static String obrigatoria(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente não definida: " + nome);
        }
        return valor;
    }
}