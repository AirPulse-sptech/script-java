package school.sptech.util;

import java.util.Locale;
import school.sptech.dto.AlertaGoldDto;

/**
 * Regras canônicas compartilhadas entre produção ({@code Main}) e backfill
 * ({@code BackfillMain}/{@code BackfillRunner}).
 * <p>
 * A regra da label é compartilhada também com o backend Node
 * ({code jiraAlertaModel.js}) — se mudar aqui, mudar lá. Há teste nos dois lados.
 */
public final class AlertUtils {

    private AlertUtils() {
    }

    /**
     * Regra canônica de label de alerta compartilhada com o backend Node.
     * Entrada: alerta_id exatamente como vem do _gold_alertas.json.
     * Saída: apenas [A-Za-z0-9._-], tudo mais vira "-", prefixado com "alerta-".
     */
    public static String sanitizarLabelAlerta(String alertaId) {
        return "alerta-" + alertaId.replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    /**
     * Monta o summary da issue. O backfill adiciona o sufixo {@code [BACKFILL]}
     * para rastreabilidade no Jira.
     *
     * @param a          evento de alerta (abertura no caso do backfill)
     * @param isBackfill true para adicionar o sufixo [BACKFILL]
     */
    public static String montarResumo(AlertaGoldDto a, boolean isBackfill) {
        String nivelExibicao = a.maiorNivel() != null ? a.maiorNivel()
                : (a.nivel() != null ? a.nivel() : "ALERTA");
        String hostname = a.hostname() != null ? a.hostname() : "?";
        String placa = a.placaSerie() != null ? a.placaSerie() : "?";
        String metrica = a.metrica() != null ? a.metrica() : "?";
        double valor = a.valor() != null ? a.valor() : 0.0;
        double limite = a.limiarViolado() != null ? a.limiarViolado() : 0.0;
        String resumo = String.format(Locale.US, "[%s] %s - %s | %s: %.1f (limite %.1f)",
                nivelExibicao, hostname, placa, metrica, valor, limite);
        return isBackfill ? resumo + " [BACKFILL]" : resumo;
    }

    /**
     * Lê variável de ambiente obrigatória (trim). Falha com mensagem clara se ausente.
     */
    public static String requiredEnv(String nome) {
        String v = System.getenv(nome);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Variável de ambiente não definida: " + nome);
        }
        return v.trim();
    }

    /**
     * Lê variável de ambiente opcional (trim); usa o padrão se ausente/vazia.
     */
    public static String envOrDefault(String nome, String padrao) {
        String v = System.getenv(nome);
        return (v == null || v.isBlank()) ? padrao : v.trim();
    }
}