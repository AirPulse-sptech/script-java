package school.sptech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import school.sptech.config.Jira;
import school.sptech.config.S3Provider;
import school.sptech.dto.AlertaGoldDto;
import school.sptech.service.AlertaConsolidator;
import school.sptech.service.GoldReader;
import school.sptech.service.S3AlertLister;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

class AlertaIntegracaoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void alertaGoldDtoDesserializaContratoComCamposNulos() throws Exception {
        String json = """
                [
                  {
                    "alerta_id": "fmc-01:PLACA_A:cpu_pct:2026-11-26T10:00:00Z",
                    "hostname": "fmc-01",
                    "placa_serie": "PLACA_A",
                    "metrica": "cpu_pct",
                    "tipo_evento": "ABERTURA",
                    "nivel": "ATENCAO",
                    "maior_nivel": "CRITICO",
                    "valor": 85.4,
                    "valor_abertura_pct": 81.2,
                    "limiar_violado": 80.0,
                    "ts_abertura": "2026-11-26T10:00:00Z",
                    "ts_evento": "2026-11-26T10:05:00Z",
                    "ts_encerramento": null,
                    "em_aberto_apos_evento": true,
                    "componente_id": null,
                    "computador_id": 12,
                    "aeronave_id": null,
                    "empresa_id": 3
                  }
                ]
                """;

        List<AlertaGoldDto> lista = MAPPER.readValue(json, new TypeReference<>() {});
        assertEquals(1, lista.size());
        AlertaGoldDto dto = lista.get(0);
        assertEquals("fmc-01:PLACA_A:cpu_pct:2026-11-26T10:00:00Z", dto.alertaId());
        assertEquals("fmc-01", dto.hostname());
        assertEquals("PLACA_A", dto.placaSerie());
        assertEquals("cpu_pct", dto.metrica());
        assertEquals("ABERTURA", dto.tipoEvento());
        assertEquals("ATENCAO", dto.nivel());
        assertEquals("CRITICO", dto.maiorNivel());
        assertEquals(85.4, dto.valor());
        assertEquals(81.2, dto.valorAberturaPct());
        assertEquals(80.0, dto.limiarViolado());
        assertEquals("2026-11-26T10:00:00Z", dto.tsAbertura());
        assertEquals("2026-11-26T10:05:00Z", dto.tsEvento());
        assertNull(dto.tsEncerramento());
        assertTrue(dto.emAbertoAposEvento());
        assertNull(dto.componenteId());
        assertEquals(12, dto.computadorId());
        assertNull(dto.aeronaveId());
        assertEquals(3, dto.empresaId());
    }

    @Test
    void s3AlertListerPaginaESemWildcard() {
        List<String> prefixesObservados = new ArrayList<>();
        S3Client mockS3 = (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[] {S3Client.class},
                (proxy, method, args) -> {
                    if ("listObjectsV2".equals(method.getName())) {
                        ListObjectsV2Request req = (ListObjectsV2Request) args[0];
                        prefixesObservados.add(req.prefix());
                        if (req.continuationToken() == null) {
                            return ListObjectsV2Response.builder()
                                    .contents(
                                            S3Object.builder().key("03-gold/hostname=fmc1/sessao=1/a_gold_alertas.json").build(),
                                            S3Object.builder().key("03-gold/hostname=fmc1/sessao=1/outro.json").build())
                                    .isTruncated(true)
                                    .nextContinuationToken("tok-2")
                                    .build();
                        }
                        return ListObjectsV2Response.builder()
                                .contents(
                                        S3Object.builder().key("03-gold/hostname=fmc1/sessao=2/b_gold_alertas.json").build())
                                .isTruncated(false)
                                .build();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        S3AlertLister lister = new S3AlertLister(mockS3);
        List<String> keys = lister.listar("meu-bucket", "03-gold/");

        assertEquals(2, keys.size());
        assertTrue(keys.contains("03-gold/hostname=fmc1/sessao=1/a_gold_alertas.json"));
        assertTrue(keys.contains("03-gold/hostname=fmc1/sessao=2/b_gold_alertas.json"));
        assertFalse(prefixesObservados.isEmpty());
        for (String p : prefixesObservados) {
            assertFalse(p.contains("*"), "Prefix S3 não pode conter wildcard *");
        }
    }

    @Test
    void consolidacaoEscolheMaisRecenteEEncerradoNaoGeraIssue() {
        AlertaGoldDto abertura = evento("id-1", "ABERTURA", "ATENCAO", "ATENCAO", "2026-11-26T10:00:00Z", true);
        AlertaGoldDto escalonamento = evento("id-1", "ESCALONAMENTO", "CRITICO", "CRITICO", "2026-11-26T10:02:00Z", true);
        AlertaGoldDto encerramento = evento("id-1", "ENCERRAMENTO", "NORMAL", "CRITICO", "2026-11-26T10:05:00Z", false);

        AlertaGoldDto rebaixamento = evento("id-2", "REBAIXAMENTO", "ATENCAO", "CRITICO", "2026-11-26T10:04:00Z", true);
        AlertaGoldDto abertura2 = evento("id-2", "ABERTURA", "CRITICO", "CRITICO", "2026-11-26T10:01:00Z", true);

        List<AlertaGoldDto> consolidados = AlertaConsolidator.consolidar(
                List.of(abertura, escalonamento, encerramento, rebaixamento, abertura2));

        assertEquals(2, consolidados.size());
        AlertaGoldDto c1 = consolidados.stream().filter(a -> "id-1".equals(a.alertaId())).findFirst().orElseThrow();
        AlertaGoldDto c2 = consolidados.stream().filter(a -> "id-2".equals(a.alertaId())).findFirst().orElseThrow();

        assertEquals("ENCERRAMENTO", c1.tipoEvento());
        assertFalse(c1.emAbertoAposEvento());

        assertEquals("REBAIXAMENTO", c2.tipoEvento());
        assertTrue(c2.emAbertoAposEvento());
    }

    @Test
    void idempotenciaMesmaOcorrenciaAbertaNaoCriaDuplicada() throws Exception {
        String jsonAberto = """
                [
                  {
                    "alerta_id": "fmc-1:PLACA_X:ram_pct:2026-11-26T10:00:00Z",
                    "hostname": "fmc-1",
                    "placa_serie": "PLACA_X",
                    "metrica": "ram_pct",
                    "tipo_evento": "ESCALONAMENTO",
                    "nivel": "CRITICO",
                    "maior_nivel": "CRITICO",
                    "valor": 95.0,
                    "valor_abertura_pct": 82.0,
                    "limiar_violado": 90.0,
                    "ts_abertura": "2026-11-26T10:00:00Z",
                    "ts_evento": "2026-11-26T10:03:00Z",
                    "ts_encerramento": null,
                    "em_aberto_apos_evento": true,
                    "componente_id": 1,
                    "computador_id": 2,
                    "aeronave_id": 3,
                    "empresa_id": 4
                  }
                ]
                """;

        S3Client mockS3 = (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[] {S3Client.class},
                (proxy, method, args) -> {
                    if ("listObjectsV2".equals(method.getName())) {
                        return ListObjectsV2Response.builder()
                                .contents(S3Object.builder().key("03-gold/hostname=fmc-1/sessao=1/s1_gold_alertas.json").build())
                                .isTruncated(false)
                                .build();
                    }
                    if ("getObject".equals(method.getName()) && args.length == 2) {
                        @SuppressWarnings("unchecked")
                        ResponseTransformer<GetObjectResponse, Object> transformer =
                                (ResponseTransformer<GetObjectResponse, Object>) args[1];
                        AbortableInputStream ais = AbortableInputStream.create(
                                new java.io.ByteArrayInputStream(jsonAberto.getBytes(StandardCharsets.UTF_8)));
                        return transformer.transform(GetObjectResponse.builder().build(), ais);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        AtomicInteger searchCount = new AtomicInteger();
        AtomicInteger createCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/rest/api/3/search/jql", exchange -> {
            int n = searchCount.incrementAndGet();
            String resp = (n == 1)
                    ? "{\"issues\":[]}"
                    : "{\"issues\":[{\"key\":\"AIR-42\",\"fields\":{\"labels\":[\"alerta-fmc-1-PLACA_X-ram_pct-2026-11-26T10-00-00Z\"]}}]}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/rest/api/3/issue", exchange -> {
            createCount.incrementAndGet();
            byte[] bytes = "{\"id\":\"10042\",\"key\":\"AIR-42\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            Jira jira = new Jira(baseUrl, "svc@airpulse.test", "fake-token", "AIR");
            S3AlertLister lister = new S3AlertLister(mockS3);
            GoldReader reader = new GoldReader(mockS3);

            Main.verificarEcriarAlertas(lister, reader, jira, "bucket", "03-gold/", "AIR", "Task");
            Main.verificarEcriarAlertas(lister, reader, jira, "bucket", "03-gold/", "AIR", "Task");

            assertEquals(2, searchCount.get());
            assertEquals(1, createCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sanitizarLabelAlertaSegueRegraCanonica() {
        assertEquals(
                "alerta-fmc-01-PLACA_A-cpu_pct-2026-11-26T10-00-00.123Z",
                Main.sanitizarLabelAlerta("fmc-01:PLACA_A:cpu_pct:2026-11-26T10:00:00.123Z"));
        assertEquals(
                "alerta-a-b_c.d-e",
                Main.sanitizarLabelAlerta("a:b_c.d-e"));
    }

    @Test
    void s3ProviderRespeitaAwsRegionConfigurada() {
        assertEquals(Region.SA_EAST_1, S3Provider.resolveRegion("sa-east-1"));
        assertEquals(Region.EU_WEST_1, S3Provider.resolveRegion(" eu-west-1 "));
        assertEquals(Region.US_EAST_1, S3Provider.resolveRegion(null));
        assertEquals(Region.US_EAST_1, S3Provider.resolveRegion("   "));
    }

    private static AlertaGoldDto evento(
            String id, String tipo, String nivel, String maiorNivel, String tsEvento, boolean aberto) {
        return new AlertaGoldDto(
                id, "fmc-1", "PLACA-1", "cpu_pct", tipo, nivel, maiorNivel,
                88.0, 82.0, 80.0, "2026-11-26T10:00:00Z", tsEvento,
                aberto ? null : tsEvento, aberto, 1, 2, 3, 4);
    }
}
