package br.com.antigravity.airpulse.alertas.backfill;

import static school.sptech.util.AlertUtils.montarResumo;
import static school.sptech.util.AlertUtils.sanitizarLabelAlerta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import school.sptech.config.Jira;
import school.sptech.dto.AlertaGoldDto;
import school.sptech.service.GoldReader;
import school.sptech.service.S3AlertLister;
import school.sptech.util.LogUtils;

/**
 * Runner para backfill único de alertas históricos no Jira.
 * <p>
 * Diferente do {@code Main} de produção, este runner:
 * <ul>
 *   <li>Processa <b>todos</b> alertas únicos por {@code alerta_id} (não filtra {@code em_aberto_apos_evento})</li>
 *   <li>Seleciona o <b>primeiro evento (abertura)</b> de cada grupo para criar a issue</li>
 *   <li>Adiciona label {@code backfill} para rastreabilidade</li>
 *   <li>Usa a mesma label canônica ({@code alerta-<id>}) → idempotência natural</li>
 * </ul>
 */
public class BackfillRunner {

    private static final String LABEL_BACKFILL = "backfill";
    private static final long PAUSA_RATE_LIMIT_MS_PADRAO = 60_000L;

    private final S3AlertLister lister;
    private final GoldReader goldReader;
    private final Jira jira;
    private final String bucket;
    private final String prefix;
    private final String jiraProjectKey;
    private final String jiraIssueType;

    /** Pausa após rate limit (429) antes do retry. Default 60s em produção. */
    private long pausaRateLimitMs = PAUSA_RATE_LIMIT_MS_PADRAO;

    private int totalEventosLidos = 0;
    private int totalAlertasUnicos = 0;
    private int issuesCriadas = 0;
    private int issuesJaExistiam = 0;
    private int erros = 0;

    public BackfillRunner(S3AlertLister lister, GoldReader goldReader, Jira jira,
                          String bucket, String prefix, String jiraProjectKey, String jiraIssueType) {
        this.lister = lister;
        this.goldReader = goldReader;
        this.jira = jira;
        this.bucket = bucket;
        this.prefix = prefix;
        this.jiraProjectKey = jiraProjectKey;
        this.jiraIssueType = jiraIssueType;
    }

    /**
     * Define a pausa após rate limit (429). Usado pelos testes para não esperar 60s.
     */
    void definirPausaRateLimit(long ms) {
        this.pausaRateLimitMs = ms;
    }

    /**
     * Executa o backfill completo.
     *
     * @return true se concluiu sem erros, false caso contrário
     */
    public boolean executar() {
        LogUtils.info("=== INÍCIO BACKFILL JIRA ===");
        LogUtils.info("Bucket: " + bucket);
        LogUtils.info("Prefixo: " + prefix);
        LogUtils.info("Projeto Jira: " + jiraProjectKey);
        LogUtils.info("Tipo de issue: " + jiraIssueType);

        try {
            // 1. Listar arquivos Gold
            List<String> keys = lister.listar(bucket, prefix);
            LogUtils.info("Arquivos Gold encontrados: " + keys.size());

            // 2. Ler todos os eventos
            List<AlertaGoldDto> todosEventos = new ArrayList<>();
            for (String key : keys) {
                try {
                    List<AlertaGoldDto> eventos = goldReader.ler(bucket, key);
                    todosEventos.addAll(eventos);
                } catch (Exception e) {
                    LogUtils.error("Erro ao ler " + key + ": " + e.getMessage());
                    erros++;
                }
            }
            totalEventosLidos = todosEventos.size();
            LogUtils.info("Eventos lidos do S3: " + totalEventosLidos);

            // 3. Agrupar por alerta_id e pegar o PRIMEIRO evento (menor ts_evento).
            // Não filtra em_aberto_apos_evento: o backfill cria issue para
            // alertas abertos E encerrados (fechamento é manual no Jira).
            Map<String, List<AlertaGoldDto>> grupos = todosEventos.stream()
                    .filter(e -> e != null && e.alertaId() != null && !e.alertaId().isBlank())
                    .collect(Collectors.groupingBy(AlertaGoldDto::alertaId));

            totalAlertasUnicos = grupos.size();
            LogUtils.info("Alerta_id únicos no dataset: " + totalAlertasUnicos);

            List<AlertaGoldDto> paraCriar = new ArrayList<>();
            for (Map.Entry<String, List<AlertaGoldDto>> entry : grupos.entrySet()) {
                AlertaGoldDto abertura = entry.getValue().stream()
                        .min((a, b) -> {
                            try {
                                return Instant.parse(a.tsEvento().trim())
                                        .compareTo(Instant.parse(b.tsEvento().trim()));
                            } catch (Exception e) {
                                return 0;
                            }
                        })
                        .orElse(null);

                if (abertura != null) {
                    paraCriar.add(abertura);
                }
            }

            LogUtils.info("Alertas com evento de abertura válido para criar issue: " + paraCriar.size());

            // 4. Criar issues no Jira (idempotente via label canônica)
            for (AlertaGoldDto alerta : paraCriar) {
                String label = sanitizarLabelAlerta(alerta.alertaId());

                try {
                    if (jira.issueExistePorLabel(label)) {
                        LogUtils.info(String.format("[EXISTE] %s | %s | %s | maior_nivel=%s | label=%s",
                                alerta.hostname(), alerta.placaSerie(), alerta.metrica(),
                                alerta.maiorNivel(), label));
                        issuesJaExistiam++;
                        continue;
                    }

                    String summary = montarResumo(alerta, true);
                    String resposta = jira.createIssue(jiraProjectKey, summary, jiraIssueType,
                            List.of(label, LABEL_BACKFILL));
                    String issueKey = jira.extrairIssueKey(resposta);

                    LogUtils.info(String.format("[CRIADA] %s | %s | %s | maior_nivel=%s | label=%s | issue=%s",
                            alerta.hostname(), alerta.placaSerie(), alerta.metrica(),
                            alerta.maiorNivel(), label, issueKey));
                    issuesCriadas++;

                } catch (Jira.JiraRateLimitException e) {
                    LogUtils.error("[RATE_LIMIT] " + e.getMessage() + " — pausando e tentando novamente...");
                    try {
                        Thread.sleep(pausaRateLimitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    // Retry uma vez
                    try {
                        String summary = montarResumo(alerta, true);
                        String resposta = jira.createIssue(jiraProjectKey, summary, jiraIssueType,
                                List.of(label, LABEL_BACKFILL));
                        String issueKey = jira.extrairIssueKey(resposta);
                        LogUtils.info(String.format("[CRIADA_RETRY] %s | issue=%s", label, issueKey));
                        issuesCriadas++;
                    } catch (Exception ex) {
                        LogUtils.error("[ERRO_RETRY] label=" + label + ": " + ex.getMessage());
                        erros++;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LogUtils.error("Interrompido durante criação de issue: " + label);
                    return false;

                } catch (Exception e) {
                    LogUtils.error(String.format("[ERRO] label=%s: %s", label, e.getMessage()));
                    erros++;
                }
            }

        } catch (Exception e) {
            LogUtils.error("Erro fatal no backfill: " + e.getMessage());
            return false;
        }

        // 5. Resumo final
        LogUtils.info("=== RESUMO BACKFILL ===");
        LogUtils.info("Eventos lidos do S3:       " + totalEventosLidos);
        LogUtils.info("Alerta_id únicos:          " + totalAlertasUnicos);
        LogUtils.info("Issues criadas:            " + issuesCriadas);
        LogUtils.info("Issues já existiam:        " + issuesJaExistiam);
        LogUtils.info("Erros:                     " + erros);

        boolean sucesso = (erros == 0);
        LogUtils.info("Status: " + (sucesso ? "SUCESSO" : "COM ERROS"));
        LogUtils.info("=== FIM BACKFILL ===");

        return sucesso;
    }
}