package school.sptech.model;

public record Componente(
      int id,
      String tipo,
      String modelo,
      double limiteAtencao,
      double limiteCritico,
      String unidade,
      String aeronave) {
}
