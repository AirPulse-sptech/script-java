package school.sptech.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LeiturasGoldDto {

    private String geradoEm;
    private List<LeituraDto> leituras = new ArrayList<>();

    public LeiturasGoldDto() {
    }

    public String getGeradoEm() {
        return geradoEm;
    }

    public void setGeradoEm(String geradoEm) {
        this.geradoEm = geradoEm;
    }

    public List<LeituraDto> getLeituras() {
        return leituras;
    }

    public void setLeituras(List<LeituraDto> leituras) {
        this.leituras = leituras;
    }
}
