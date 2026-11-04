#!/usr/bin/env bash
set -euo pipefail
mkdir -p .ci-speech-models presentation/build/generated/asr-fixtures
MODEL=.ci-speech-models/ggml-tiny.bin
if ! printf '%s  %s\n' bd577a113a864445d4c299885e0cb97d4ba92b5f "$MODEL" | sha1sum --check --status; then
  curl --fail --location --retry 3 --max-time 240 https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin --output "$MODEL.part"
  printf '%s  %s\n' bd577a113a864445d4c299885e0cb97d4ba92b5f "$MODEL.part" | sha1sum --check
  mv "$MODEL.part" "$MODEL"
fi
printf '%s  %s\n' bd577a113a864445d4c299885e0cb97d4ba92b5f "$MODEL" | sha1sum --check
cp "$MODEL" presentation/build/generated/asr-fixtures/ggml-tiny.bin
curl --fail --location --retry 3 --max-time 120 https://raw.githubusercontent.com/ggml-org/whisper.cpp/d1be6fde11ac6e0407606b4e42fe72d34add8037/samples/jfk.wav --output presentation/build/generated/asr-fixtures/jfk.wav
