# Guia de Deploy na AWS EC2 - AirPulse

Este guia contém o passo a passo completo para configurar sua instância EC2 na AWS, instalar o Docker e o MySQL diretamente no sistema operacional da máquina, e executar as imagens Docker da AirPulse.

---

## 1. Configurar Security Group na AWS (Firewall)

No Console da AWS, certifique-se de que a **Security Group** da sua EC2 tenha as seguintes portas liberadas nas **Regras de Entrada (Inbound Rules)**:

| Tipo | Porta | Protocolo | Origem | Finalidade |
|---|---|---|---|---|
| SSH | 22 | TCP | Meu IP (ou `0.0.0.0/0`) | Acesso remoto via terminal |
| Custom TCP | 3333 | TCP | `0.0.0.0/0` | Acesso ao site Web (Node.js) |

> [!IMPORTANT]
> **Não é necessário abrir a porta 3306 (MySQL) para a internet (`0.0.0.0/0`)!**  
> Como o MySQL e os containers estarão na mesma máquina EC2, a comunicação entre eles é interna, mantendo seu banco seguro contra invasões externas.

---

## 2. Acessar a EC2 e Instalar Docker e MySQL

Conecte-se na sua instância via SSH:
```bash
ssh -i "sua-chave.pem" ubuntu@SEU_IP_PUBLICO_EC2
```

### 2.1 Atualizar o sistema
```bash
sudo apt update && sudo apt upgrade -y
```

### 2.2 Instalar o Docker
```bash
# Instala o Docker
sudo apt install docker.io -y

# Habilita e inicia o serviço
sudo systemctl start docker
sudo systemctl enable docker

# Permite rodar comandos docker sem precisar digitar 'sudo' toda vez
sudo usermod -aG docker $USER
```
*(Saia do SSH com `exit` e entre novamente para ativar a permissão do Docker).*

### 2.3 Instalar o MySQL Server nativo na EC2
```bash
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql
```

---

## 3. Configurar o MySQL para aceitar conexões dos Containers

Por padrão, o MySQL do Ubuntu só escuta conexões locais restritas (`127.0.0.1`). Precisamos permitir que os containers Docker se conectem a ele.

### 3.1 Liberar o bind-address do MySQL
Edite o arquivo de configuração do MySQL:
```bash
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf
```
Procure a linha:
```ini
bind-address = 127.0.0.1
```
Altere para:
```ini
bind-address = 0.0.0.0
```
*(Salve com `Ctrl + O`, aperte `Enter` e saia com `Ctrl + X`).*

Reinicie o MySQL para aplicar:
```bash
sudo systemctl restart mysql
```

### 3.2 Criar o Banco, Tabelas e Usuário de Acesso

Acesse o console do MySQL:
```bash
sudo mysql
```

Execute os comandos SQL abaixo:
```sql
-- 1. Criação da database
CREATE DATABASE IF NOT EXISTS airpulse;

-- 2. Criação do usuário para conexões externas/containers
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'SuaSenhaSegura123';
GRANT ALL PRIVILEGES ON airpulse.* TO 'root'@'%';
FLUSH PRIVILEGES;

-- 3. Selecionar o banco para importar suas tabelas
USE airpulse;
```

> [!TIP]
> Cole aqui o script DDL com as tabelas da AirPulse (`aeronave`, `computador`, `placa`, `componente`, `metrica`, etc.) e insira os dados iniciais. Depois, saia do MySQL:
> ```sql
> EXIT;
> ```

---

## 4. Baixar as Imagens do Docker Hub

Na EC2, baixe as imagens que você subiu:

```bash
# Caso o repositório seja privado, faça login antes:
docker login

# Baixar a imagem dos alertas Java:
docker pull mateusloualmeida/airpulse_java_alertas:airpulse

# Baixar a imagem do Web Site (Node):
docker pull mateusloualmeida/airpulse_eduardo:airpulse
```

---

## 5. Como Executar os Containers

> [!TIP]
> **Dica de Ouro no Linux (EC2): `--network host`**  
> Ao rodar com a flag `--network host`, o container compartilha diretamente a rede da EC2.  
> Com isso, a conexão com o banco fica simplesmente `localhost:3306`, exatamente como se a aplicação estivesse rodando fora do Docker!

### 5.1 Rodando o Site Web (Node.js)
O site roda como serviço contínuo em segundo plano (`-d`):

```bash
docker run -d \
  --name airpulse-web \
  --restart unless-stopped \
  -p 3333:3333 \
  -e DB_HOST="172.17.0.1" \
  -e DB_USER="root" \
  -e DB_PASSWORD="SuaSenhaSegura123" \
  -e DB_DATABASE="airpulse" \
  mateusloualmeida/airpulse_eduardo:airpulse
```
*(Ou usando `--network host` para usar `localhost`)*

---

### 5.2 Rodando o Script de Alertas (Java)

O script Java executa o processamento do S3, verifica os limites no banco e envia as notificações para o Jira.

Rodando via `--network host` (acessando o MySQL direto em `localhost`):

```bash
docker run --rm \
  --network host \
  -e JIRA_API_TOKEN="SEU_TOKEN_DO_ATLASSIAN" \
  -e AWS_ACCESS_KEY_ID="SUA_ACCESS_KEY_ID" \
  -e AWS_SECRET_ACCESS_KEY="SUA_SECRET_ACCESS_KEY" \
  -e AWS_SESSION_TOKEN="SEU_SESSION_TOKEN" \
  -e DB_URL="jdbc:mysql://localhost:3306/airpulse" \
  -e DB_PASSWORD="SuaSenhaSegura123" \
  mateusloualmeida/airpulse_java_alertas:airpulse
```

---

## 6. Dicas Úteis de Operação na EC2

### Ver se o container está rodando:
```bash
docker ps
```

### Ver os logs do site web:
```bash
docker logs -f airpulse-web
```

### Agendar o script Java no `crontab` da EC2 (ex: rodar a cada 5 minutos):
Abra o agendador:
```bash
crontab -e
```
Adicione a linha no final:
```cron
*/5 * * * * docker run --rm --network host -e JIRA_API_TOKEN="TOKEN" -e AWS_ACCESS_KEY_ID="..." -e AWS_SECRET_ACCESS_KEY="..." -e AWS_SESSION_TOKEN="..." -e DB_URL="jdbc:mysql://localhost:3306/airpulse" -e DB_PASSWORD="..." mateusloualmeida/airpulse_java_alertas:airpulse >> /home/ubuntu/alertas.log 2>&1
```
