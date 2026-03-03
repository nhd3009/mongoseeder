FROM ghcr.io/graalvm/native-image-community:25 AS builder

RUN microdnf install -y maven

WORKDIR /build
COPY . .

RUN mvn -Pnative native:compile -DskipTests

FROM oraclelinux:9-slim

WORKDIR /app

COPY --from=builder /build/target/mongoseeder /app/mongoseeder

RUN chmod +x /app/mongoseeder

ENTRYPOINT ["/app/mongoseeder"]