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

import grpc

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
DEFAULT_TIMEOUT_SECONDS = 10.0


def classify(text: str, address: str = DEFAULT_ADDRESS,
             timeout: float = DEFAULT_TIMEOUT_SECONDS) -> "classifier_pb2.ClassifyResponse":
    """Sends `text` to the ClassifierService and returns the response message."""
    with grpc.insecure_channel(address) as channel:
        stub = classifier_pb2_grpc.ClassifierServiceStub(channel)
        request = classifier_pb2.ClassifyRequest(text=text)
        return stub.Classify(request, timeout=timeout)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Validate a spec string against the ClassifierService gRPC server."
    )
    parser.add_argument(
        "text",
        nargs="?",
        help="Text to validate. If omitted, it is read from stdin.",
    )
    parser.add_argument(
        "--address",
        default=DEFAULT_ADDRESS,
        help=f"Server address as host:port (default: {DEFAULT_ADDRESS})",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_TIMEOUT_SECONDS,
        help=f"RPC timeout in seconds (default: {DEFAULT_TIMEOUT_SECONDS})",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Only print the final result string, no extra detail.",
    )
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)

    text = args.text
    if text is None:
        text = sys.stdin.read() # .strip()

    if not text:
        sys.stderr.write("No input text provided (arg or stdin).\n")
        return 2

    try:
        response = classify(text, address=args.address, timeout=args.timeout)
    except grpc.RpcError as e:
        sys.stderr.write(f"gRPC call failed: {e.code()} - {e.details()}\n")
        return 3

    if args.quiet:
        print(response.result)
    else:
        print(f"Input:    {text!r}")
        print(f"Result:   {response.result}")

    return 0 


if __name__ == "__main__":
    sys.exit(main())
