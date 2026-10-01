#!/usr/bin/env python3
"""
spec_validator.py

Minimal gRPC client for the ClassifierService. Sends a single string
("spec") to the Java server, which runs it through every Classifier
instance in its list and returns an aggregated result. This script prints
the result and exits with a status code reflecting whether the server
accepted the input, so it can be used directly in shell scripts / CI.

Usage:
    python spec_validator.py "some string to validate"
    echo "some string" | python spec_validator.py
    python spec_validator.py --address localhost:50051 "some string"

Before running, generate the Python protobuf/gRPC stubs (see
generate_protos.sh or the README) so that classifier_pb2.py and
classifier_pb2_grpc.py exist in this directory.
"""

import argparse
import sys
import subprocess
from typing import List

import grpc
import re

# Program parameters that must be referenced
# in every candidate expression.
PROGRAM_PARAMS = ("s", "elem", "out")

try:
    import classifier_pb2
    import classifier_pb2_grpc
except ImportError:
    sys.stderr.write(
        "Could not import classifier_pb2 / classifier_pb2_grpc.\n"
        "Generate them first, e.g.:\n"
        "    python -m grpc_tools.protoc -I../src/main/proto "
        "--python_out=. --grpc_python_out=. ../src/main/proto/classifier.proto\n"
        "or run ./generate_protos.sh from this directory.\n"
    )
    raise


DEFAULT_ADDRESS = "localhost:50051"
DEFAULT_TIMEOUT_SECONDS = 600
DEFAULT_FANDANGO_BIN = "/opt/anaconda3/bin/fandango"

# --------------------------------------------------------------------------
# gRPC call
# --------------------------------------------------------------------------
def classify(text: str, address: str = DEFAULT_ADDRESS,
             timeout: float = DEFAULT_TIMEOUT_SECONDS) -> "classifier_pb2.ClassifyResponse":
    """Sends `text` to the ClassifierService and returns the response message."""
    with grpc.insecure_channel(address) as channel:
        stub = classifier_pb2_grpc.ClassifierServiceStub(channel)
        request = classifier_pb2.ClassifyRequest(text=text)
        return stub.Classify(request, timeout=timeout)


def maximize_fitness(candidate_pc: str) -> int:
    try:

        if not candidate_pc:
            sys.stderr.write("No input text provided (arg or stdin).\n")
            return -1
        try:
            response = classify(candidate_pc, address=DEFAULT_ADDRESS, timeout=DEFAULT_TIMEOUT_SECONDS)
        except grpc.RpcError as e:
            sys.stderr.write(f"gRPC call failed: {e.code()} - {e.details()}\n")
            return -1

        result = response.result
        if not result: # result.returncode != 0:
            print("No result returned from server.")
            return 0

        #output = result.strip()

        pos_pass_m = re.search(r'pos_pass=\s*(\d+)', result)
        neg_reject_m = re.search(r'neg_reject=\s*(\d+)', result)
        total_m = re.search(r'total=\s*(\d+)', result)
        pos_fail_m = re.search(r'pos_fail=\s*(\d+)', result)
        neg_fail_m = re.search(r'neg_fail=\s*(\d+)', result)

        if not (pos_pass_m and neg_reject_m and total_m and pos_fail_m and neg_fail_m):
            print("Could not parse result string:", result)
            return 0

        pos_pass = int(pos_pass_m.group(1))
        neg_reject = int(neg_reject_m.group(1))
        total = int(total_m.group(1))
        pos_fail = int(pos_fail_m.group(1))
        neg_fail = int(neg_fail_m.group(1))
        print(f"pos_pass={pos_pass}, neg_reject={neg_reject}")

        # Reward specifications that pass all positive instances and reject all negative instances.
        return pos_pass + neg_reject

    except Exception as e:
        return 0


    

