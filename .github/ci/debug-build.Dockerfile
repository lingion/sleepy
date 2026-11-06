# 只安装构建所需的系统库；JDK 和 Android SDK 从 runner 只读挂载。
FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl git unzip zip libstdc++6 zlib1g libzstd1 \
    && rm -rf /var/lib/apt/lists/*
