package school.sptech.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertaDto(
        @JsonProperty("alerta_id") String alertaId,
        String metrica,
        @JsonProperty("tipo_evento") String tipoEvento,
        Double valor,
        @JsonProperty("ts_evento") String tsEvento,
        String fase,
        @JsonProperty("componente_id") Integer componenteId) {
}