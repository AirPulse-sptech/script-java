package school.sptech;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class Logs {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int opcao = 0;

        while (opcao != 4) {

            LocalDateTime dataHora = LocalDateTime.now();
            DateTimeFormatter formatar = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String dataHoraFormatada = dataHora.format(formatar);

            System.out.println("\n------------------------------------------------");
            System.out.println("Selecione o tipo de log que deseja visualizar:");
            System.out.println("1 - Logs arquivo de captura");
            System.out.println("2 - Logs arquivo de leitura");
            System.out.println("3 - Logs conexão com banco de dados");
            System.out.println("4 - Sair");
            System.out.print("Digite um número: ");

            opcao = scanner.nextInt();
            System.out.println("------------------------------------------------\n");

            if (opcao == 1) {
                System.out.println(dataHoraFormatada + " - INFO - Iniciando processo de captura dos componentes de hardware");
                System.out.println(dataHoraFormatada + " - INFO - Métricas capturadas com sucesso!");
                System.out.println(dataHoraFormatada + " - INFO - Arquivo para leitura criado em: ./dados_Aviao01.csv");
                System.out.println(dataHoraFormatada + " - ERROR - Falha ao gravar linha no arquivo ./dados_Aviao01.csv - Erro de Permissão");
                System.out.println(dataHoraFormatada + " - INFO - Captura de dados finalizada com sucesso. Total de linhas gravadas: 50");

            } else if (opcao == 2) {
                System.out.println(dataHoraFormatada + " - INFO - Iniciando leitura dos arquivos no diretório capturas-Aviao01-csv/");
                System.out.println(dataHoraFormatada + " - ERROR - Falha ao ler o arquivo dados_Aviao01.csv - Cabeçalho inválido ou arquivo corrompido");
                System.out.println(dataHoraFormatada + " - INFO - Dados consolidados com sucesso. Total de registros carregados: 200");

            } else if (opcao == 3) {


                System.out.println(dataHoraFormatada + " - INFO - Iniciando conexão com o banco de dados");


                System.out.println(dataHoraFormatada + " - INFO - Banco de dados localizado em: localhost:3306");
                System.out.println(dataHoraFormatada + " - INFO - Conexão com o banco de dados estabelecida com sucesso");
                System.out.println(dataHoraFormatada + " - INFO - Verificando disponibilidade das tabelas do AirPulse");
                System.out.println(dataHoraFormatada + " - INFO - Tabela de métricas encontrada com sucesso");
                System.out.println(dataHoraFormatada + " - INFO - Iniciando persistência das métricas capturadas");
                System.out.println(dataHoraFormatada + " - INFO - Métricas inseridas no banco de dados com sucesso. Total de registros: 50");


                System.out.println(dataHoraFormatada + " - ERROR - Falha ao inserir métricas no banco de dados - Tempo limite de conexão excedido");


                System.out.println(dataHoraFormatada + " - INFO - Processo de persistência finalizado");


                System.out.println(dataHoraFormatada + " - INFO - Conexão com o banco de dados encerrada");

            } else if (opcao == 4) {
                System.out.println("Encerrando a execução...");
            }
        }

        scanner.close();
    }
}

