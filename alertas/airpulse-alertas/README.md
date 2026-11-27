# AirPulse — Serviço Java de Alertas (Jira)

Serviço que lê os arquivos Gold de alertas no S3 e cria/issues no Jira.

## Modos de execução

### Produção (scheduler de 1 min)

```
ENTRYPOINT da imagem: java -jar /app/app.jar  (classe school.sptech.Main)
```

Regra:
1. Lista `*_gold_alertas.json` no bucket/prefixo
2. Consolida eventos por `alerta_id` (fica com o mais recente)
3. **Cria issue apenas para `em_aberto_apos_evento == true`** (alertas abertos)
4. Idempotência: pula se já existir issue com a label canônica `alerta-<id>`

### Backfill (execução única)

```
docker compose run --rm --entrypoint "" alertas-java \
  java -cp /app/app.jar \
  br.com.antigravity.airpulse.alertas.backfill.BackfillMain
```

Regra:
1. Lista e lê **todos** os arquivos Gold
2. Agrupa por `alerta_id` e pega o **primeiro evento** (abertura, menor `ts_evento`)
3. **Cria issue para todos** (abertos **e** encerrados — o fechamento é manual no Jira)
4. Idempotência pela mesma label canônica; adiciona label `backfill` e sufixo `[BACKFILL]` no summary

## Build (Maven roda dentro do Docker — multi-stage)

```bash
./build-and-push.sh
```

Faz:
1. `docker build` (multi-stage: Maven compila + gera fat JAR → JRE runtime)
2. **Sanity check**: roda o `BackfillMain` dentro da imagem e confirma que a classe existe no JAR
3. Push das tags `v<data>-<gitshort>` e `latest` para o Docker Hub

## Variáveis de ambiente

| Variável | Produção | Backfill | Default |
|----------|:--------:|:--------:|---------|
| `JIRA_BASE_URL` | ✅ | ✅ | — |
| `JIRA_EMAIL` | ✅ | ✅ | — |
| `JIRA_API_TOKEN` | ✅ | ✅ | — |
| `JIRA_ALERT_PROJECT_KEY` | ✅ | ✅ | — |
| `JIRA_ALERT_ISSUE_TYPE` | ✅ | ✅ | `Task` |
| `aws_s3_bucket` | ✅ | ✅ | — |
| `aws_s3_alertas_prefix` | ✅ | ✅ | `03-gold/` |
| `aws_region` | ✅ | ✅ | `us-east-1` |

Credenciais AWS: `DefaultCredentialsProvider` (env vars `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_SESSION_TOKEN` ou role da EC2).

## Deploy na EC2

```
web-site-airpulse/deployjava.sh <IP_EC2> [--run-backfill]
```
- Pull-based (não builda na EC2)
- Usa `deploy-java.env` (não sobrescreve o `deploy.env` do Node)

Rollback: fixe `JAVA_IMAGE_TAG=<tag anterior>` antes de rodar o deploy.

## Label canônica (compartilhada com o backend Node)

```
alerta-<alerta_id com [^A-Za-z0-9._-] trocado por "-">
```
Ex.: `fmc-01:PLACA_A:cpu_pct:2026-11-26T10:00:00.123Z` → `alerta-fmc-01-PLACA_A-cpu_pct-2026-11-26T10-00-00.123Z`

Há teste nos dois lados (Java: `AlertaIntegracaoTest`; Node: `jiraIntegracao.test.js`) garantindo a mesma regra.

## Estrutura

```
src/main/java/school/sptech/
  Main.java                 # scheduler produção (1 min)
  config/Jira.java          # client REST Jira (criar/buscar por label)
  config/S3Provider.java    # client S3 (DefaultCredentialsProvider)
  service/                  # lister, reader, consolidador
  util/                     # LogUtils + AlertUtils (regras canônicas compartilhadas)
src/main/java/br/com/antigravity/airpulse/alertas/backfill/
  BackfillMain.java         # entry point do backfill
  BackfillRunner.java       # lógica do backfill (todos os alerta_id)
src/test/java/              # testes de integração (S3 mockado + HttpServer Jira falso)
```