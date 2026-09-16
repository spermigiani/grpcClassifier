# gRPC Classifier Service

A minimal gRPC service: a **Java server** that classifies incoming strings
using an external `classifier.jar` library, and a **Python client**
(`spec_validator.py`) that talks to it.

## How it works

- On startup, the server builds a list, `instanceToClassify`, of
  `com.example.classifier.Classifier` objects loaded from
  `libs/classifier.jar` (3 instances by default).
- For each incoming `Classify` RPC, it calls `classify(text)` on every
  instance in that list, takes a majority vote, and returns a result
  string (plus a boolean `accepted` flag and the raw per-classifier votes)
  to the client.
- The Python client sends a string to the server and prints/validates the
  result, exiting `0` if accepted and `1` otherwise (handy for scripting).

```
grpc-classifier-project/
├── build.gradle                       # main Gradle build (server)
├── settings.gradle
├── src/main/proto/classifier.proto    # shared service/message definitions
├── src/main/java/...ClassifierServer.java        # server entry point
├── src/main/java/...ClassifierServiceImpl.java   # RPC implementation
├── external-classifier/               # placeholder "classifier.jar" source
│   ├── src/main/java/.../Classifier.java
│   └── build_jar.sh                   # builds libs/classifier.jar
├── libs/classifier.jar                # (pre-built placeholder included)
└── python_client/
    ├── spec_validator.py              # the gRPC client
    ├── requirements.txt
    └── generate_protos.sh             # regenerates *_pb2*.py from the .proto
```

## Prerequisites

- JDK 11+ and Gradle (or use the included wrapper once generated, see below)
- Python 3.8+
- Network access to Maven Central / PyPI to fetch dependencies

## 1. Provide `classifier.jar`

This repo ships a **placeholder** `libs/classifier.jar` (source in
`external-classifier/`) so the project runs out of the box. Its `classify()`
method has no real semantics — swap it out:

```bash
# Rebuild the placeholder yourself:
cd external-classifier
./build_jar.sh   # writes ../libs/classifier.jar

# OR, drop in your real library:
cp /path/to/real/classifier.jar libs/classifier.jar
```

The only contract the server relies on is: a public class
`com.example.classifier.Classifier` with a public no-arg constructor and a
public `boolean classify(String)` method. If your real library uses a
different package/class name, update the `import` in
`ClassifierServiceImpl.java` accordingly.

## 2. Build and run the Java server

This project uses the Gradle `protobuf` plugin, which downloads `protoc`
and the `protoc-gen-grpc-java` plugin automatically and generates the
gRPC/protobuf Java classes from `src/main/proto/classifier.proto` at build
time — no generated code is checked in.

```bash
# If you don't have Gradle installed, generate the wrapper once with a
# local Gradle install: `gradle wrapper --gradle-version 8.7`
./gradlew run
# or:
gradle run
```

You should see:

```
INFO: Initialized 3 Classifier instance(s).
INFO: ClassifierServer started, listening on port 50051
```

To build a standalone runnable jar instead:

```bash
./gradlew shadowJar
java -jar build/libs/classifier-server-1.0.0-all.jar
```

The port defaults to `50051`. Override it with an argument or env var:

```bash
java -jar build/libs/classifier-server-1.0.0-all.jar 60051
CLASSIFIER_SERVER_PORT=60051 java -jar build/libs/classifier-server-1.0.0-all.jar
```

## 3. Set up and run the Python client

```bash
cd python_client
python3 -m venv .venv && source .venv/bin/activate   # optional
pip install -r requirements.txt

# Generate classifier_pb2.py / classifier_pb2_grpc.py from the shared .proto
./generate_protos.sh

python spec_validator.py "some string to classify"
# or:
echo "some string" | python spec_validator.py --address localhost:50051
```

Example output:

```
Input:    'some string to classify'
Result:   VALID (2/3 classifiers agree)
Accepted: True
Votes:    [True, True, False]
```

Exit code is `0` when `accepted` is true, `1` when false, `2`/`3` on
input/RPC errors — so it can be dropped straight into a CI/validation
pipeline (hence the name `spec_validator.py`).

## Customizing

- **Number of classifier instances**: change `NUM_CLASSIFIERS` in
  `ClassifierServiceImpl.java`.
- **Aggregation rule**: currently strict majority vote; edit the
  `accepted` computation in `ClassifierServiceImpl.classify(...)` for
  unanimous / any-true / weighted logic, etc.
- **Message shape**: edit `src/main/proto/classifier.proto` and regenerate
  both sides (`./gradlew build` on the Java side regenerates
  automatically; rerun `generate_protos.sh` on the Python side).

## Notes on this build environment

`libs/classifier.jar` in this repo was compiled and packaged for real and
verified. The Python client (`spec_validator.py`) was verified end-to-end
against a live server implementing the same `.proto` contract. The Java
gRPC server code follows standard grpc-java generated-code conventions
(`XxxServiceGrpc.XxxServiceImplBase`, `StreamObserver`, `ServerBuilder`) —
compiling it requires network access to Maven Central to fetch
`grpc-netty-shaded`, `grpc-protobuf`, `grpc-stub`, and `protoc`, which run
automatically the first time you execute `./gradlew run` or
`./gradlew build` with normal internet access.
