package br.com.antigravity.airpulse.alertas.backfill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import school.sptech.config.Jira;
import school.sptech.service.GoldReader;
import school.sptech.service.S3AlertLister;
import school.sptech.util.AlertUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

class BackfillRunnerTest {

    private static final String JSON_EVENTOS = """
            [
              {
                "alerta_id": "fmc-1:PLACA_X:ram_pct:2026-11-26T10:00:00Z",
                "hostname": "fmc-1",
                "placa_serie": "PLACA_X",
                "metrica": "ram_pct",
                "tipo_evento": "ABERTURA",
                "nivel": "ATENCAO",
                "maior_nivel": "CRITICO",
                "valor": 85.0,
                "valor_abertura_pct": 85.0,
                "limiar_violado": 90.0,
                "ts_abertura": "2026-11-26T10:00:00Z",
                "ts_evento": "2026-11-26T10:00:00Z",
                "ts_encerramento": null,
                "em_aberto_apos_evento": true,
                "computador_id": 10,
                "aeronave_id": 20,
                "empresa_id": 1
              },
              {
                "alerta_id": "fmc-1:PLACA_X:ram_pct:2026-11-26T10:00:00Z",
                "hostname": "fmc-1",
                "placa_serie": "PLACA_X",
                "metrica": "ram_pct",
                "tipo_evento": "ESCALONAMENTO",
                "nivel": "CRITICO",
                "maior_nivel": "CRITICO",
                "valor": 95.0,
                "valor_abertura_pct": 85.0,
                "limiar_violado": 90.0,
                "ts_abertura": "2026-11-26T10:00:00Z",
                "ts_evento": "2026-11-26T10:05:00Z",
                "ts_encerramento": null,
                "em_aberto_apos_evento": true,
                "computador_id": 10,
                "aeronave_id": 20,
                "empresa_id": 1
              },
              {
                "alerta_id": "fmc-1:PLACA_X:flash_pct:2026-11-26T09:00:00Z",
                "hostname": "fmc-1",
                "placa_serie": "PLACA_X",
                "metrica": "flash_pct",
                "tipo_evento": "ENCERRAMENTO",
                "nivel": "NORMAL",
                "maior_nivel": "ATENCAO",
                "valor": 91.0,
                "valor_abertura_pct": 91.0,
                "limiar_violado": 80.0,
                "ts_abertura": "2026-11-26T09:00:00Z",
                "ts_evento": "2026-11-26T09:30:00Z",
                "ts_encerramento": "2026-11-26T09:30:00Z",
                "em_aberto_apos_evento": false,
                "computador_id": 10,
                "aeronave_id": 20,
                "empresa_id": 1
              }
            ]
            """;

    private static S3Client mockS3ComEventos() {
        return (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[] {S3Client.class},
                (proxy, method, args) -> {
                    if ("listObjectsV2".equals(method.getName())) {
                        return ListObjectsV2Response.builder()
                                .contents(S3Object.builder()
                                        .key("03-gold/hostname=fmc-1/sessao=1/s1_gold_alertas.json").build())
                                .isTruncated(false)
                                .build();
                    }
                    if ("getObject".equals(method.getName()) && args.length == 2) {
                        @SuppressWarnings("unchecked")
                        ResponseTransformer<GetObjectResponse, Object> transformer =
                                (ResponseTransformer<GetObjectResponse, Object>) args[1];
                        AbortableInputStream ais = AbortableInputStream.create(
                                new java.io.ByteArrayInputStream(
                                        JSON_EVENTOS.getBytes(StandardCharsets.UTF_8)));
                        return transformer.transform(GetObjectResponse.builder().build(), ais);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Jira falso baseado em HttpServer (mesmo padrão do AlertaIntegracaoTest).
     * <ul>
     *   <li>{@code corposCriacao}: bodies das criações BEM-SUCEDIDAS (201)</li>
     *   <li>{@code tentativasCriacao}: total de POSTs em /issue (inclui 429)</li>
     *   <li>{@code labelsExistentes}: labels que a busca deve reportar como já existentes</li>
     * </ul>
     */
    private record JiraFalso(
            HttpServer server,
            List<String> jqlsRecebidas,
            List<String> corposCriacao,
            List<String> labelsExistentes,
            AtomicInteger tentativasCriacao,
            boolean rateLimitNaPrimeiraTentativa) {

        static JiraFalso criar(boolean rateLimitNaPrimeiraTentativa) throws Exception {
            List<String> jqlsRecebidas = new ArrayList<>();
            List<String> corposCriacao = new ArrayList<>();
            List<String> labelsExistentes = new ArrayList<>();
            AtomicInteger tentativasCriacao = new AtomicInteger();

            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);

            server.createContext("/rest/api/3/search/jql", exchange -> {
                String jql = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                jqlsRecebidas.add(jql);
                boolean existe = labelsExistentes.stream().anyMatch(jql::contains);
                String resp = existe
                        ? "{\"issues\":[{\"key\":\"KAN-9\",\"fields\":{\"labels\":[]}}]}"
                        : "{\"issues\":[]}";
                byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            server.createContext("/rest/api/3/issue", exchange -> {
                String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int attempt = tentativasCriacao.incrementAndGet();
                boolean rateLimited = rateLimitNaPrimeiraTentativa && attempt == 1;
                if (!rateLimited) {
                    corposCriacao.add(corpo);
                }
                int status = rateLimited ? 429 : 201;
                byte[] bytes = "{\"id\":\"1\",\"key\":\"KAN-42\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            server.start();
            return new JiraFalso(server, jqlsRecebidas, corposCriacao, labelsExistentes,
                    tentativasCriacao, rateLimitNaPrimeiraTentativa);
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void parar() {
            server.stop(0);
        }
    }

    private BackfillRunner novoRunner(S3Client s3, Jira jira) {
        return new BackfillRunner(
                new S3AlertLister(s3), new GoldReader(s3), jira,
                "bucket", "03-gold/", "KAN", "Task");
    }

    @Test
    void backfillCriaIssueParaTodosAlertasUsandoEventoDeAbertura() throws Exception {
        JiraFalso jira = JiraFalso.criar(false);
        try {
            S3Client s3 = mockS3ComEventos();
            BackfillRunner runner = novoRunner(s3,
                    new Jira(jira.baseUrl(), "svc@airpulse.test", "fake", "KAN"));

            boolean sucesso = runner.executar();
            assertTrue(sucesso);

            // 2 alerta_id únicos → 2 issues (uma inclusive do alerta encerrado)
            assertEquals(2, jira.corposCriacao().size());
            assertEquals(2, jira.tentativasCriacao().get(), "Sem rate limit: 2 POSTs apenas");

            // Usa o evento de ABERTURA (85.0), não o escalonamento (95.0)
            String issueRam = jira.corposCriacao().stream()
                    .filter(c -> c.contains("ram_pct-2026-11-26T10-00-00Z"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(issueRam.contains("85.0"), "Summary deve usar o valor da abertura");
            assertFalse(issueRam.contains("95.0"), "Summary não deve usar o valor do escalonamento");
            assertTrue(issueRam.contains("[BACKFILL]"), "Summary do backfill deve ter o sufixo");
            assertTrue(issueRam.contains("\"backfill\""), "Issue do backfill deve ter a label backfill");

            // Alerta encerrado também ganha issue (regra do backfill: criar para todos)
            assertTrue(jira.corposCriacao().stream()
                            .anyMatch(c -> c.contains("flash_pct-2026-11-26T09-00-00Z")),
                    "Alerta com em_aberto_apos_evento=false também deve gerar issue");
        } finally {
            jira.parar();
        }
    }

    @Test
    void backfillNaoRecriaIssueExistente() throws Exception {
        JiraFalso jira = JiraFalso.criar(false);
        try {
            S3Client s3 = mockS3ComEventos();
            BackfillRunner runner = novoRunner(s3,
                    new Jira(jira.baseUrl(), "svc@airpulse.test", "fake", "KAN"));

            // 1ª execução: cria as 2 issues
            assertTrue(runner.executar());
            assertEquals(2, jira.corposCriacao().size());

            // Simula issues já existentes no Jira (busca encontra as labels)
            jira.labelsExistentes().add(
                    AlertUtils.sanitizarLabelAlerta("fmc-1:PLACA_X:ram_pct:2026-11-26T10:00:00Z"));
            jira.labelsExistentes().add(
                    AlertUtils.sanitizarLabelAlerta("fmc-1:PLACA_X:flash_pct:2026-11-26T09:00:00Z"));

            // 2ª execução: idempotente via label canônica
            assertTrue(runner.executar());
            assertEquals(2, jira.corposCriacao().size(),
                    "Labels canônicas já existentes não devem gerar novas issues");
        } finally {
            jira.parar();
        }
    }

    @Test
    void backfillFazRetryAposRateLimit() throws Exception {
        JiraFalso jira = JiraFalso.criar(true);
        try {
            S3Client s3 = mockS3ComEventos();
            BackfillRunner runner = novoRunner(s3,
                    new Jira(jira.baseUrl(), "svc@airpulse.test", "fake", "KAN"));
            runner.definirPausaRateLimit(1); // não esperar 60s no teste

            boolean sucesso = runner.executar();
            assertTrue(sucesso);
            // 1º POST: 429 → pausa → retry 201 (2 tentativas p/ o 1º alerta)
            // 2º alerta: 201 direto (1 tentativa) → 3 no total, 2 criadas
            assertEquals(3, jira.tentativasCriacao().get());
            assertEquals(2, jira.corposCriacao().size());
        } finally {
            jira.parar();
        }
    }

    @Test
    void backfillRetornaFalseEmErroFatal() throws Exception {
        S3Client s3Quebrado = (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[] {S3Client.class},
                (proxy, method, args) -> {
                    if ("listObjectsV2".equals(method.getName())) {
                        throw new RuntimeException("S3 indisponível");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        Jira jira = new Jira("http://localhost:1", "svc@airpulse.test", "fake", "KAN");
        BackfillRunner runner = novoRunner(s3Quebrado, jira);

        assertFalse(runner.executar(), "Erro fatal no S3 deve retornar false");
    }
}