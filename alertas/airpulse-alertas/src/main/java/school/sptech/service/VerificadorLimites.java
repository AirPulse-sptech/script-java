package school.sptech.service;

import school.sptech.model.Componente;
import school.sptech.model.Nivel;

public class VerificadorLimites {
    public static Nivel classificar(double valor, Componente componente) {
        if (valor >= componente.limiteCritico()) {
            return Nivel.CRITICO;
        }
        if (valor >= componente.limiteAtencao()) {
            return Nivel.ATENCAO;
        }
        return Nivel.NORMAL;
    }
}
