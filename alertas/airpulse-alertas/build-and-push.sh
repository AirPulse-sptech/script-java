#!/bin/bash
set -e

echo "=== BUILD & PUSH DOCKER HUB (airpulse-alertas) ==="

IMAGE="mateusloualmeida/airpulse-alertas"

# Tag versionada: v<YYYYMMDD-HHmm>-<gitshort> (fallback: timestamp)
DATA=$(date +%Y%m%d-%H%M)
GIT_SHORT=$(git rev-parse --short HEAD 2>/dev/null || echo "")
if [ -n "$GIT_SHORT" ]; then
  TAG="v${DATA}-${GIT_SHORT}"
else
  TAG="v${DATA}"
fi

echo "[1/3] docker build (Maven roda DENTRO do Docker, multi-stage)"
docker build -t "${IMAGE}:${TAG}" -t "${IMAGE}:latest" .

echo "[2/3] Sanity check: confirmar que as classes estão no JAR"
# O BackfillMain exige variáveis de ambiente; se a classe existir no JAR,
# falha apenas por config ausente. Se NÃO existir, dá ClassNotFoundException.
# ATENÇÃO: --entrypoint "" é OBRIGATÓRIO — sem ele o ENTRYPOINT (java -jar app.jar)
# engole o comando e executa o Main de produção com o resto como argumentos.
docker run --rm --entrypoint "" "${IMAGE}:${TAG}" \
  java -cp /app/app.jar br.com.antigravity.airpulse.alertas.backfill.BackfillMain 2>&1 | \
  tee /tmp/backfill-sanity.log
if grep -q "ClassNotFoundException\|Could not find or load main class" /tmp/backfill-sanity.log; then
  echo ""
  echo "❌ SANITY CHECK FALHOU: BackfillMain não está no JAR. Push abortado."
  exit 1
fi
if grep -q "Variável de ambiente não definida: aws_s3_bucket" /tmp/backfill-sanity.log; then
  echo "✅ Sanity check OK: classe encontrada no JAR (falhou apenas por credenciais, como esperado)."
else
  echo "⚠️  Sanity check inconclusivo (mensagem inesperada). Revise o log acima."
  echo "    Push abortado por segurança."
  exit 1
fi
rm -f /tmp/backfill-sanity.log

echo "[3/3] docker push ${IMAGE}:${TAG} e :latest"
docker push "${IMAGE}:${TAG}"
docker push "${IMAGE}:latest"

echo ""
echo "✅ Imagem publicada: ${IMAGE}:${TAG} (+ :latest)"
echo ""
echo "Para atualizar na EC2:"
echo "  cd web-site-airpulse && bash deployjava.sh <IP_EC2>"
echo ""
echo "Para rodar o backfill:"
echo "  bash deployjava.sh <IP_EC2> --run-backfill"
echo ""
echo "Para fixar uma versão específica (rollback possível):"
echo "  JAVA_IMAGE_TAG=${TAG} bash deployjava.sh <IP_EC2>"
echo ""
echo "Rollback rápido: exporte JAVA_IMAGE_TAG=<tag anterior> e rerode deployjava.sh"