#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_DIR="${1:-$ROOT_DIR/.local-models/bge-reranker-base}"
BASE_URL="https://huggingface.co/Xenova/bge-reranker-base/resolve/main"

mkdir -p "$MODEL_DIR/onnx"

curl -L --fail --retry 5 --retry-delay 3 --retry-all-errors --connect-timeout 20 \
  -o "$MODEL_DIR/tokenizer.json" \
  "$BASE_URL/tokenizer.json"

curl -L --fail --retry 5 --retry-delay 3 --retry-all-errors --connect-timeout 20 \
  -C - \
  -o "$MODEL_DIR/onnx/model_quantized.onnx" \
  "$BASE_URL/onnx/model_quantized.onnx"

printf 'BGE reranker base model downloaded to %s\n' "$MODEL_DIR"
printf 'Model URI: file:%s/onnx/model_quantized.onnx\n' "$MODEL_DIR"
printf 'Tokenizer URI: file:%s/tokenizer.json\n' "$MODEL_DIR"
