package school.sptech.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import school.sptech.dto.AlertaGoldDto;
import school.sptech.util.LogUtils;

/**
 * Consolida uma lista de eventos Gold de alerta, retornando um único evento
 * por {alerta_id}, no caso, aquele com o  ts_evento} mais recente.

 * Validação de identidade: todos os eventos com o mesmo {alerta_id}
 * devem compartilhar { hostname}, {placaSerie}, {aeronaveId},
 * { computadorId} e {Abertura}. Grupos inconsistentes ou com
 * timestamps inválidos são descartados e logados.
 */
public class AlertaConsolidator {

    private AlertaConsolidator() {
    }

    /**
     * Resultado da consolidação, com contadores de descarte para observabilidade.
     */
    public record ConsolidationResult(
            List<AlertaGoldDto> consolidados,
            int gruposInconsistentes,
            int timestampsInvalidos) {
    }

    /**
     * Consolida descartando grupos inconsistentes/invalidos, silenciosamente
     * quanto aos contadores (compatível com comportamento anterior).
     */
    public static List<AlertaGoldDto> consolidar(List<AlertaGoldDto> eventos) {
        return consolidarComResumo(eventos).consolidados();
    }

    /**
     * Igual a {@link #consolidar(List)}, mas retorna também os contadores
     * de grupos descartados (identidade inconsistente / ts_evento inválido).
     */
    public static ConsolidationResult consolidarComResumo(List<AlertaGoldDto> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return new ConsolidationResult(List.of(), 0, 0);
        }

        Map<String, List<AlertaGoldDto>> grupos = eventos.stream()
                .filter(e -> e != null && e.alertaId() != null && !e.alertaId().isBlank())
                .collect(Collectors.groupingBy(AlertaGoldDto::alertaId));

        List<AlertaGoldDto> consolidados = new ArrayList<>();
        int gruposInconsistentes = 0;
        int timestampsInvalidos = 0;

        for (Map.Entry<String, List<AlertaGoldDto>> entry : grupos.entrySet()) {
            String alertaId = entry.getKey();
            List<AlertaGoldDto> grupo = entry.getValue();

            if (!identidadeConsistente(grupo)) {
                LogUtils.warn(String.format(
                        "[CONSOLIDAR] alerta_id=%s descartado — identidade inconsistente entre %d eventos",
                        alertaId, grupo.size()));
                gruposInconsistentes++;
                continue;
            }

            AlertaGoldDto maisRecente = null;
            Instant maiorTs = null;
            boolean timestampInvalido = false;

            for (AlertaGoldDto evento : grupo) {
                Instant ts = parseInstant(evento.tsEvento());
                if (ts == null) {
                    LogUtils.warn(String.format(
                            "[CONSOLIDAR] alerta_id=%s descartado — ts_evento inválido: %s",
                            alertaId, evento.tsEvento()));
                    timestampInvalido = true;
                    break;
                }
                if (maiorTs == null || ts.isAfter(maiorTs)) {
                    maiorTs = ts;
                    maisRecente = evento;
                }
            }

            if (timestampInvalido) {
                timestampsInvalidos++;
                continue;
            }

            if (maisRecente != null) {
                consolidados.add(maisRecente);
            }
        }

        return new ConsolidationResult(consolidados, gruposInconsistentes, timestampsInvalidos);
    }

    private static Instant parseInstant(String ts) {
        if (ts == null || ts.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(ts.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean identidadeConsistente(List<AlertaGoldDto> grupo) {
        if (grupo.size() <= 1) {
            return true;
        }

        AlertaGoldDto ref = grupo.get(0);
        for (int i = 1; i < grupo.size(); i++) {
            AlertaGoldDto outro = grupo.get(i);
            if (!Objects.equals(ref.hostname(), outro.hostname())
                    || !Objects.equals(ref.placaSerie(), outro.placaSerie())
                    || !Objects.equals(ref.aeronaveId(), outro.aeronaveId())
                    || !Objects.equals(ref.computadorId(), outro.computadorId())
                    || !Objects.equals(ref.tsAbertura(), outro.tsAbertura())) {
                return false;
            }
        }
        return true;
    }
}