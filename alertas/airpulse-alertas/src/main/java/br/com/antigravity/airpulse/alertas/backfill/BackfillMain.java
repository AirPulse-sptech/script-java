package br.com.antigravity.airpulse.alertas.backfill;

import static school.sptech.util.AlertUtils.envOrDefault;
import static school.sptech.util.AlertUtils.requiredEnv;

import school.sptech.config.Jira;
import school.sptech.config.S3Provider;
import school.sptech.service.GoldReader;
import school.sptech.service.S3AlertLister;
import school.sptech.util.LogUtils;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Entry point para o backfill único de alertas históricos no Jira.
 * <p>
 * Execução via imagem Docker (produção do backfill):
 * {@code docker compose run --rm --entrypoint "" alertas-java
 * java -cp /app/app.jar br.com.antigravity.airpulse.alertas.backfill.BackfillMain}
 * <p>
 * Regra: cria uma issue para cada {@code alerta_id} único encontrado no bucket
 * Gold (abertos ou encerrados), usando o primeiro evento (abertura) do grupo,
 * com idempotência pela label canônica {@code alerta-<id>}.
 */
public class BackfillMain {

    public static void main(String[] args) {
        String rawBucket = requiredEnv("aws_s3_bucket");
        final String bucket = rawBucket.startsWith("s3://") ? rawBucket.substring(5) : rawBucket;
        String prefix = envOrDefault("aws_s3_alertas_prefix", "03-gold/");
        String jiraBaseUrl = requiredEnv("JIRA_BASE_URL");
        String jiraEmail = requiredEnv("JIRA_EMAIL");
        String jiraToken = requiredEnv("JIRA_API_TOKEN");
        String jiraProjectKey = requiredEnv("JIRA_ALERT_PROJECT_KEY");
        String jiraIssueType = envOrDefault("JIRA_ALERT_ISSUE_TYPE", "Task");

        S3Client s3Client = new S3Provider().getS3Client();
        S3AlertLister lister = new S3AlertLister(s3Client);
        GoldReader goldReader = new GoldReader(s3Client);
        Jira jira = new Jira(jiraBaseUrl, jiraEmail, jiraToken, jiraProjectKey);

        BackfillRunner runner = new BackfillRunner(
                lister, goldReader, jira,
                bucket, prefix, jiraProjectKey, jiraIssueType);

        int exitCode;
        try {
            boolean sucesso = runner.executar();
            exitCode = sucesso ? 0 : 1;
        } finally {
            try {
                s3Client.close();
            } catch (Exception e) {
                LogUtils.error("Erro ao fechar S3Client: " + e.getMessage());
            }
        }

        System.exit(exitCode);
    }
}