# 只安装构建所需的系统库；JDK 和 Android SDK 从 runner 只读挂载。
FROM ubuntu:24.04
# GitHub runner 对 *.ubuntu.com 的内网 DNS 解析间歇性失效 (2026-11-11 连续 6 红);
# 先按默认源走, 失败则改用公共 DNS (1.1.1.1/8.8.8.8) 重试一次。
RUN set -eux; \
    if ! apt-get update; then \
        printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf; \
        apt-get update; \
    fi; \
    apt-get install -y --no-install-recommends \
    ca-certificates curl git unzip zip libstdc++6 zlib1g libzstd1; \
    rm -rf /var/lib/apt/lists/*
