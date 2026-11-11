# 只安装构建所需的系统库；JDK 和 Android SDK 从 runner 只读挂载。
FROM ubuntu:24.04
# GitHub runner 内网 DNS 对 *.ubuntu.com 间歇性失效 (2026-11-11 连续 6 红)。
# 实测: apt-get update 全源失败时仍返回 0, 只有 install 才非零; 因此以 install
# 失败为触发, 写入公共 DNS (1.1.1.1/8.8.8.8) 后整体重试一次; 再失败则红给日志。
RUN set -eux; \
    pkgs='ca-certificates curl git unzip zip libstdc++6 zlib1g libzstd1'; \
    apt-get update || true; \
    if ! apt-get install -y --no-install-recommends $pkgs; then \
        printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf; \
        apt-get update && apt-get install -y --no-install-recommends $pkgs; \
    fi; \
    rm -rf /var/lib/apt/lists/*
