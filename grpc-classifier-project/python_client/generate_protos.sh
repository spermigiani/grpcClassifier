#!/usr/bin/env bash
# Regenerates classifier_pb2.py and classifier_pb2_grpc.py from the shared
# .proto file. Run this from inside python_client/ after `pip install -r
# requirements.txt`, and again any time classifier.proto changes.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROTO_DIR="${SCRIPT_DIR}/../src/main/proto"

python -m grpc_tools.protoc \
  -I "${PROTO_DIR}" \
  --python_out="${SCRIPT_DIR}" \
  --grpc_python_out="${SCRIPT_DIR}" \
  "${PROTO_DIR}/classifier.proto"

echo "Generated classifier_pb2.py and classifier_pb2_grpc.py in ${SCRIPT_DIR}"
