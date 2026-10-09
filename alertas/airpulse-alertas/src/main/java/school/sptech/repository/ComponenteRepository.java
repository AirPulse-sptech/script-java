package school.sptech.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import school.sptech.model.Componente;

public class ComponenteRepository implements AutoCloseable {

    private static final String SQL = """
          SELECT c.idComponente, c.tipo, c.modelo, c.limiteAtencao, c.limiteCritico,
                 a.nome AS aeronave,
                 (SELECT m.unidadeMedida FROM metrica m
                   WHERE m.fkComponente = c.idComponente LIMIT 1) AS unidade
          FROM componente c
          JOIN placa p ON p.idPlaca = c.fkPlaca
          JOIN computador pc ON pc.idComputador = p.fkComputador
          JOIN aeronave a ON a.idAeronave = pc.fkAeronave
          WHERE c.idComponente = ?
          """;

    private final Connection connection;

    public ComponenteRepository(String url, String usuario, String senha) throws SQLException {
        this.connection = DriverManager.getConnection(url, usuario, senha);
    }

    public Optional<Componente> buscarPorId(int idComponente) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SQL)) {
            stmt.setInt(1, idComponente);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Componente(
                      rs.getInt("idComponente"),
                      rs.getString("tipo"),
                      rs.getString("modelo"),
                      limite(rs.getBigDecimal("limiteAtencao")),
                      limite(rs.getBigDecimal("limiteCritico")),
                      rs.getString("unidade"),
                      rs.getString("aeronave")));
            }
        }
    }

    private static double limite(BigDecimal valor) {
        return valor == null ? Double.MAX_VALUE : valor.doubleValue();
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
