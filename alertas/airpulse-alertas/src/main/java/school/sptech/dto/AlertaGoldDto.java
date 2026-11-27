package school.sptech.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO imutável que mapeia o contrato JSON da camada Gold de alertas.
 * Cada registro representa um evento de alerta já enriquecido pelo pipeline de dados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertaGoldDto(
        @JsonProperty("alerta_id") String alertaId,
        @JsonProperty("hostname") String hostname,
        @JsonProperty("placa_serie") String placaSerie,
        @JsonProperty("metrica") String metrica,
        @JsonProperty("tipo_evento") String tipoEvento,
        @JsonProperty("nivel") String nivel,
        @JsonProperty("maior_nivel") String maiorNivel,
        @JsonProperty("valor") Double valor,
        @JsonProperty("valor_abertura_pct") Double valorAberturaPct,
        @JsonProperty("limiar_violado") Double limiarViolado,
        @JsonProperty("ts_abertura") String tsAbertura,
        @JsonProperty("ts_evento") String tsEvento,
        @JsonProperty("ts_encerramento") String tsEncerramento,
        @JsonProperty("em_aberto_apos_evento") Boolean emAbertoAposEvento,
        @JsonProperty("componente_id") Integer componenteId,
        @JsonProperty("computador_id") Integer computadorId,
        @JsonProperty("aeronave_id") Integer aeronaveId,
        @JsonProperty("empresa_id") Integer empresaId) {
}
