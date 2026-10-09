# Sleepy v1.0.62-test

> Test release for CI signing verification fix

## What's New

- CI signing verification fix: derive expected SHA-256 from secret keystore at runtime, instead of hardcoding

## Fixes

- CI release workflow: fix signing certificate verification to use secret keystore as single source of truth

## Verification

- Tests: CI verification only
- Build: v1.0.62-test

---

# Sleepy v1.0.62-test

> CI 签名校验修复测试

## 新增功能

- CI 签名校验修复：从 secret keystore 运行时派生 expected SHA-256，取代硬编码

## 修复

- CI 发布流程：修复签名证书校验，使用 secret keystore 作为唯一真源

## 验证

- 测试：仅 CI 验证
- 构建：v1.0.62-test
