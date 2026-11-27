package school.sptech;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import school.sptech.config.Jira;
import school.sptech.config.S3Provider;
import school.sptech.dto.AlertaGoldDto;
import school.sptech.service.AlertaConsolidator;
import school.sptech.service.GoldReader;
import school.sptech.service.S3AlertLister;
import school.sptech.util.AlertUtils;
import school.sptech.util.LogUtils;
import software.amazon.awssdk.services.s3.S3Client;

public class Main {

    public static void main(String[] args) {
        String rawBucket = AlertUtils.requiredEnv("aws_s3_bucket");
        final String bucket = rawBucket.startsWith("s3://") ? rawBucket.substring(5) : rawBucket;
        String prefix = AlertUtils.envOrDefault("aws_s3_alertas_prefix", "03-gold/");
        String jiraBaseUrl = AlertUtils.requiredEnv("JIRA_BASE_URL");
        String jiraEmail = AlertUtils.requiredEnv("JIRA_EMAIL");
        String jiraToken = AlertUtils.requiredEnv("JIRA_API_TOKEN");
        String jiraProjectKey = AlertUtils.requiredEnv("JIRA_ALERT_PROJECT_KEY");
        String jiraIssueType = AlertUtils.envOrDefault("JIRA_ALERT_ISSUE_TYPE", "Task");

        S3Client s3Client = new S3Provider().getS3Client();
        S3AlertLister lister = new S3AlertLister(s3Client);
        GoldReader goldReader = new GoldReader(s3Client);
        Jira jira = new Jira(jiraBaseUrl, jiraEmail, jiraToken, jiraProjectKey);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                verificarEcriarAlertas(lister, goldReader, jira, bucket, prefix, jiraProjectKey, jiraIssueType);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdown();
            } catch (Exception e) {
                LogUtils.error("Erro no ciclo de sincronização Jira: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.MINUTES);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            s3Client.close();
        }));
    }

    static void verificarEcriarAlertas(
            S3AlertLister lister,
            GoldReader goldReader,
            Jira jira,
            String bucket,
            String prefix,
            String jiraProjectKey,
            String jiraIssueType) throws InterruptedException {
        List<String> keys = lister.listar(bucket, prefix);
        LogUtils.info("Arquivos Gold encontrados: " + keys.size());

        List<AlertaGoldDto> todosEventos = new ArrayList<>();
        for (String key : keys) {
            try {
                todosEventos.addAll(goldReader.ler(bucket, key));
            } catch (Exception e) {
                LogUtils.error("Erro ao ler " + key + ": " + e.getMessage());
            }
        }
        LogUtils.info("Total de eventos lidos: " + todosEventos.size());

        AlertaConsolidator.ConsolidationResult consolidacao =
                AlertaConsolidator.consolidarComResumo(todosEventos);
        List<AlertaGoldDto> consolidados = consolidacao.consolidados();
        LogUtils.info("Alertas consolidados: " + consolidados.size());
        if (consolidacao.gruposInconsistentes() > 0 || consolidacao.timestampsInvalidos() > 0) {
            LogUtils.warn(String.format(
                    "Consolidação com descartes: grupos_inconsistentes=%d, ts_invalidos=%d",
                    consolidacao.gruposInconsistentes(), consolidacao.timestampsInvalidos()));
        }

        List<AlertaGoldDto> abertos = consolidados.stream()
                .filter(a -> Boolean.TRUE.equals(a.emAbertoAposEvento()))
                .toList();
        LogUtils.info("Alertas abertos: " + abertos.size());

        int criadas = 0;
        for (AlertaGoldDto alerta : abertos) {
            String label = sanitizarLabelAlerta(alerta.alertaId());
            try {
                if (jira.issueExistePorLabel(label)) {
                    LogUtils.info(String.format("[EXISTE] %s | %s | %s | maior_nivel=%s | label=%s",
                            alerta.hostname(), alerta.placaSerie(), alerta.metrica(), alerta.maiorNivel(), label));
                    continue;
                }

                String summary = AlertUtils.montarResumo(alerta, false);
                String resposta = jira.createIssue(jiraProjectKey, summary, jiraIssueType, List.of(label));
                String issueKey = jira.extrairIssueKey(resposta);
                LogUtils.info(String.format("[CRIADA] %s | %s | %s | maior_nivel=%s | label=%s | issue=%s",
                        alerta.hostname(), alerta.placaSerie(), alerta.metrica(), alerta.maiorNivel(), label, issueKey));
                criadas++;
            } catch (InterruptedException e) {
                throw e;
            } catch (Jira.JiraRateLimitException e) {
                LogUtils.error("[RATE_LIMIT] " + e.getMessage() + ". Aguardando próximo ciclo.");
                break;
            } catch (HttpTimeoutException e) {
                LogUtils.error(String.format("[TIMEOUT] label=%s (%s). Consultando label antes de prosseguir.",
                        label, e.getMessage()));
                try {
                    if (jira.issueExistePorLabel(label)) {
                        LogUtils.info(String.format("[EXISTE_APOS_TIMEOUT] label=%s já foi criada.", label));
                    }
                } catch (InterruptedException ie) {
                    throw ie;
                } catch (Exception ignored) {
                    // Próximo ciclo reavaliará
                }
            } catch (Exception e) {
                LogUtils.error(String.format("[ERRO] label=%s: %s", label, e.getMessage()));
            }
        }
        LogUtils.info("Issues criadas neste ciclo: " + criadas);
    }

    /**
     * Regra canônica compartilhada com o backend Node.
     * Delega para {@link AlertUtils#sanitizarLabelAlerta(String)}.
     */
    public static String sanitizarLabelAlerta(String alertaId) {
        return AlertUtils.sanitizarLabelAlerta(alertaId);
    }
}