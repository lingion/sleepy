package main

// selfupdate.go: 启动自检 — 本二进制 SHA256 与线上 dist/sha256sums.txt 对比。
//
// 背景 (2026-09-16 用户指令): "以后的采集器要自己采集, 并和线上版本的 SHA 对比
// 是否对不上。如果对不上, 就更新到线上 SHA 一致为止"。
//
// 动线: 启动 → 算自身 SHA256 → 拉 https://raw.githubusercontent.com/lingion/sleepy/main/
// tools/sleepy-collector/dist/sha256sums.txt (raw.githubusercontent.com 走镜像
// gh.qdp.qzz.io, 直连被墙时自动换) → 对比本平台条目:
//   一致        → 静默通过, 照常采集
//   对不上      → 下载线上同平台二进制 → 校验下载体 SHA == 线上清单 SHA
//               → 原子替换自身 (win: 旧改名 .old; nix: 直接 rename 覆盖)
//               → 提示用户重新启动新版本, 本进程退出 (不采旧包)
//   线上不可达  → 打警告但不拦 (离线/内网同学仍可采集, 版本写在包 INDEX.txt 里)
//
// 平台识别: GOOS/GOARCH → dist 命名 {linux-amd64, macos-amd64, macos-arm64,
// windows-amd64.exe}; sha256sums.txt 条目路径形如 dist/<name>, 按文件名匹配。

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const (
	// 镜像序: gh.qdp.qzz.io raw 路由未实现 (2026-09-16 实测 Not Found) →
	// 公共 raw 镜像 ghproxy.net / gh-proxy.com → 直连兜底。
	selfUpdateBaseURLs = "https://ghproxy.net/https://raw.githubusercontent.com/lingion/sleepy/main/tools/sleepy-collector/dist/," +
		"https://gh-proxy.com/https://raw.githubusercontent.com/lingion/sleepy/main/tools/sleepy-collector/dist/," +
		"https://raw.githubusercontent.com/lingion/sleepy/main/tools/sleepy-collector/dist/"
	selfUpdateTimeout = 30 * time.Second
)

// distBinaryName 返回 dist 目录里本平台的二进制文件名 (sha256sums.txt 条目文件名)。
func distBinaryName() string {
	return distBinaryNameFor(runtime.GOOS, runtime.GOARCH)
}

func distBinaryNameFor(goos, arch string) string {
	osName := goos
	switch osName {
	case "darwin":
		osName = "macos"
	}
	name := "sleepy-collector-" + osName + "-" + arch
	if goos == "windows" {
		name += ".exe"
	}
	return name
}

// fetchSelfUpdateURL 依次尝试镜像列表, 返回第一个取到 body 的结果。
func fetchSelfUpdateURL(rel string) ([]byte, error) {
	var lastErr error
	for _, base := range strings.Split(selfUpdateBaseURLs, ",") {
		url := base + rel
		body, err := httpGet(context.Background(), url, selfUpdateTimeout)
		if err == nil && len(body) > 0 {
			return body, nil
		}
		if err != nil {
			lastErr = err
		} else {
			lastErr = errors.New("empty body from " + url)
		}
	}
	if lastErr == nil {
		lastErr = errors.New("all mirrors failed")
	}
	return nil, lastErr
}

// selfSHA256 算当前可执行文件的 SHA256。
func selfSHA256() (string, error) {
	exe, err := os.Executable()
	if err != nil {
		return "", err
	}
	f, err := os.Open(exe)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// parseSha256Sums 从 sha256sums.txt 内容里取指定文件名的 SHA。
// 行形如 "<hex>  dist/<name>", 文件名匹配取最后一段。
func parseSha256Sums(content string, binaryName string) string {
	for _, line := range strings.Split(content, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		parts := strings.Fields(line)
		if len(parts) != 2 {
			continue
		}
		if filepath.Base(parts[1]) == binaryName {
			return strings.ToLower(parts[0])
		}
	}
	return ""
}

// selfCheckAndUpdate 执行自检。返回值:
//
//	ok=true               → SHA 一致 (或无法检查), 照常继续
//	ok=false, needRestart → 已下载新版并替换, 调用方应提示用户重启后退出
func selfCheckAndUpdate() (ok bool, updated bool, err error) {
	selfSum, err := selfSHA256()
	if err != nil {
		return true, false, fmt.Errorf("无法计算自身 SHA: %w", err)
	}
	listBody, err := fetchSelfUpdateURL("sha256sums.txt")
	if err != nil {
		// 线上不可达: 不拦采集, 只警告
		fmt.Println("⚠ 无法访问线上版本清单 (离线/内网? 跳过自检):", err)
		return true, false, nil
	}
	want := parseSha256Sums(string(listBody), distBinaryName())
	if want == "" {
		fmt.Println("⚠ 线上清单里没有本平台条目 (", distBinaryName(), "), 跳过自检")
		return true, false, nil
	}
	if strings.EqualFold(selfSum, want) {
		fmt.Println("✓ 版本自检通过: 本地与线上一致 (", distBinaryName(), ")")
		return true, false, nil
	}

	fmt.Println()
	fmt.Println("=====================================================")
	fmt.Println("⚠ 本采集器版本与线上不一致")
	fmt.Println("  本地 :", selfSum)
	fmt.Println("  线上 :", want)
	fmt.Println("正在自动更新到线上版本…")
	fmt.Println("=====================================================")

	bin, err := fetchSelfUpdateURL(distBinaryName())
	if err != nil {
		return true, false, fmt.Errorf("下载新版失败: %w", err)
	}
	sum := sha256.Sum256(bin)
	got := hex.EncodeToString(sum[:])
	if !strings.EqualFold(got, want) {
		return true, false, fmt.Errorf("下载体 SHA 不匹配线上清单 (got %s want %s), 放弃替换", got, want)
	}

	exe, err := os.Executable()
	if err != nil {
		return true, false, err
	}
	exe, _ = filepath.Abs(exe)
	if runtime.GOOS == "windows" {
		// Windows 不能覆盖运行中的 exe: 旧文件改名让位, 新体写到原名
		old := exe + ".old"
		_ = os.Remove(old)
		if err := os.Rename(exe, old); err != nil {
			return true, false, fmt.Errorf("旧版改名失败: %w", err)
		}
	}
	// POSIX 原子替换: 写同目录临时文件再 rename。rename 对运行中的 exe 合法,
	// 而直写运行中 exe 在 Linux 报 ETXTBSY (text file busy), macOS 才允许直写。
	tmp := exe + ".new"
	if err := os.WriteFile(tmp, bin, 0o755); err != nil {
		return true, false, fmt.Errorf("写入新版失败: %w", err)
	}
	if err := os.Rename(tmp, exe); err != nil {
		_ = os.Remove(tmp)
		return true, false, fmt.Errorf("替换新版失败: %w", err)
	}
	return false, true, nil
}
