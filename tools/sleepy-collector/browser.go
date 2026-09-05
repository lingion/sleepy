// browser.go: 浏览器内核定位与自动下载
//
// 优先用本机已装的浏览器(Chrome / Edge / Chromium);都没有时,
// 自动下载 Google 官方的 Chrome for Testing 内核(约 190MB)到用户缓存目录。
// 下载源:国内优先 npmmirror 镜像(直连快),失败退官方 storage.googleapis.com。
package main

import (
	"archive/zip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

// findBrowser 找一个可用的浏览器可执行文件;找不到时自动下载内核。
// SLEEPY_COLLECTOR_BROWSER 环境变量可强制指定路径(测试/便携版用)。
func findBrowser() (string, error) {
	if p := os.Getenv("SLEEPY_COLLECTOR_BROWSER"); p != "" {
		if fileExecutable(p) {
			return p, nil
		}
		return "", fmt.Errorf("SLEEPY_COLLECTOR_BROWSER 指定的浏览器不存在: %s", p)
	}
	// 1) 本机已装?
	for _, p := range candidatePaths() {
		if fileExecutable(p) {
			return p, nil
		}
	}
	// 2) 自动下载
	fmt.Println()
	fmt.Println("本机没有找到 Chrome / Edge 浏览器。")
	fmt.Println("正在自动下载官方浏览器内核(约 190MB,只需下载一次,请保持网络畅通)…")
	return downloadCftChrome()
}

// candidatePaths 返回本机可能的浏览器路径,按优先级。
func candidatePaths() []string {
	switch runtime.GOOS {
	case "darwin":
		home, _ := os.UserHomeDir()
		return []string{
			"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
			"/Applications/Chromium.app/Contents/MacOS/Chromium",
			"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
			home + "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
			home + "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
		}
	case "windows":
		home := os.Getenv("USERPROFILE")
		var out []string
		for _, base := range []string{
			`C:\Program Files\Google\Chrome\Application\chrome.exe`,
			`C:\Program Files (x86)\Google\Chrome\Application\chrome.exe`,
			`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`,
			`C:\Program Files\Microsoft\Edge\Application\msedge.exe`,
			home + `\AppData\Local\Google\Chrome\Application\chrome.exe`,
			home + `\AppData\Local\Microsoft\Edge\Application\msedge.exe`,
		} {
			out = append(out, base)
		}
		return out
	default: // linux
		return []string{
			"google-chrome", "google-chrome-stable", "google-chrome-beta",
			"chromium", "chromium-browser", "chrome",
			"microsoft-edge", "microsoft-edge-stable",
			"/usr/bin/google-chrome", "/usr/bin/google-chrome-stable",
			"/usr/bin/chromium", "/usr/bin/chromium-browser",
			"/usr/bin/microsoft-edge", "/usr/bin/microsoft-edge-stable",
			"/snap/bin/chromium", "/opt/google/chrome/chrome",
		}
	}
}

func fileExecutable(p string) bool {
	if !strings.ContainsRune(p, filepath.Separator) && !strings.ContainsRune(p, '/') {
		// PATH 查找(如 "google-chrome")
		found, err := exec.LookPath(p)
		if err != nil {
			return false
		}
		_ = found
		return true
	}
	info, err := os.Stat(p)
	return err == nil && !info.IsDir()
}

// cftVersion 取 Chrome for Testing 稳定版版本号。
func cftVersion(ctx context.Context) (string, error) {
	urls := []string{
		"https://registry.npmmirror.com/-/binary/chrome-for-testing/last-known-good-versions.json",
		"https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions.json",
	}
	for _, u := range urls {
		body, err := httpGet(ctx, u, 20*time.Second)
		if err != nil {
			continue
		}
		var doc struct {
			Channels struct {
				Stable struct {
					Version string `json:"version"`
				} `json:"Stable"`
			} `json:"channels"`
		}
		if json.Unmarshal(body, &doc) == nil && doc.Channels.Stable.Version != "" {
			return doc.Channels.Stable.Version, nil
		}
	}
	return "", fmt.Errorf("无法获取版本信息(请检查网络)")
}

// downloadCftChrome 下载并解包 Chrome for Testing,返回可执行文件路径。
func downloadCftChrome() (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Minute)
	defer cancel()

	ver, err := cftVersion(ctx)
	if err != nil {
		return "", err
	}
	platform, exeRel, err := cftPlatform()
	if err != nil {
		return "", err
	}

	cacheRoot := cftCacheDir()
	exePath := filepath.Join(cacheRoot, ver, exeRel)
	if fileExecutable(exePath) {
		fmt.Println("浏览器内核已就绪:", exePath)
		return exePath, nil
	}

	zipName := "chrome-" + platform + ".zip"
	rel := ver + "/" + platform + "/" + zipName
	mirrors := []string{
		"https://registry.npmmirror.com/-/binary/chrome-for-testing/" + rel,
		"https://storage.googleapis.com/chrome-for-testing-public/" + rel,
	}
	zipPath := filepath.Join(cacheRoot, ver+".zip")
	var lastErr error
	downloaded := false
	for _, u := range mirrors {
		fmt.Println("  下载源:", u)
		start := time.Now()
		lastErr = httpDownload(ctx, u, zipPath, 15*time.Minute)
		if lastErr == nil {
			fmt.Printf("  下载完成(%.0f 秒)\n", time.Since(start).Seconds())
			downloaded = true
			break
		}
		fmt.Println("  该下载源失败:", lastErr)
	}
	if !downloaded {
		return "", fmt.Errorf("所有下载源均失败,请检查网络后重试(最后错误: %v)", lastErr)
	}

	fmt.Println("  正在解压…")
	if err := unzipTo(zipPath, filepath.Join(cacheRoot, ver)); err != nil {
		return "", fmt.Errorf("解压失败: %v", err)
	}
	os.Remove(zipPath)
	if !fileExecutable(exePath) {
		return "", fmt.Errorf("解压后未找到浏览器: %s", exePath)
	}
	if runtime.GOOS != "windows" {
		os.Chmod(exePath, 0755)
	}
	fmt.Println("浏览器内核就绪:", exePath)
	return exePath, nil
}

func cftPlatform() (platform, exeRel string, err error) {
	switch runtime.GOOS {
	case "darwin":
		arm := runtime.GOARCH == "arm64"
		if arm {
			return "mac-arm64", "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing", nil
		}
		return "mac-x64", "chrome-mac-x64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing", nil
	case "windows":
		return "win64", `chrome-win64\chrome.exe`, nil
	case "linux":
		return "linux64", "chrome-linux64/chrome", nil
	}
	return "", "", fmt.Errorf("暂不支持系统: %s", runtime.GOOS)
}

func cftCacheDir() string {
	base := os.Getenv("XDG_CACHE_HOME")
	if base == "" {
		home, _ := os.UserHomeDir()
		switch runtime.GOOS {
		case "darwin":
			base = filepath.Join(home, "Library", "Caches")
		case "windows":
			base = filepath.Join(home, "AppData", "Local")
		default:
			base = filepath.Join(home, ".cache")
		}
	}
	dir := filepath.Join(base, "sleepy-collector", "chrome")
	os.MkdirAll(dir, 0755)
	return dir
}

func httpClient() *http.Client {
	return &http.Client{Timeout: 30 * time.Second}
}

func httpGet(ctx context.Context, url string, timeout time.Duration) ([]byte, error) {
	cctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	req, err := http.NewRequestWithContext(cctx, "GET", url, nil)
	if err != nil {
		return nil, err
	}
	resp, err := httpClient().Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	return io.ReadAll(resp.Body)
}

// httpDownload 流式下载到 dest(先写临时文件,成功后改名)。
func httpDownload(ctx context.Context, url, dest string, timeout time.Duration) error {
	cctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	req, err := http.NewRequestWithContext(cctx, "GET", url, nil)
	if err != nil {
		return err
	}
	resp, err := httpClient().Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	tmp := dest + ".part"
	f, err := os.Create(tmp)
	if err != nil {
		return err
	}
	total := resp.ContentLength
	var done int64
	chunk := make([]byte, 256*1024)
	tick := time.Now()
	for {
		n, rerr := resp.Body.Read(chunk)
		if n > 0 {
			if _, werr := f.Write(chunk[:n]); werr != nil {
				f.Close()
				return werr
			}
			done += int64(n)
			if total > 0 && time.Since(tick) > 5*time.Second {
				fmt.Printf("  进度: %d%%\n", int(done*100/total))
				tick = time.Now()
			}
		}
		if rerr != nil {
			if rerr == io.EOF {
				break
			}
			f.Close()
			return rerr
		}
	}
	f.Close()
	if total > 0 && done != total {
		return fmt.Errorf("下载不完整: %d/%d 字节", done, total)
	}
	return os.Rename(tmp, dest)
}

// unzipTo 解压 zip 到 dest(处理 zip 内目录结构)。
func unzipTo(zipPath, dest string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		name := filepath.Clean(f.Name)
		if strings.HasPrefix(name, "..") || filepath.IsAbs(name) {
			continue // 防 zip 路径穿越
		}
		target := filepath.Join(dest, name)
		if f.FileInfo().IsDir() {
			os.MkdirAll(target, 0755)
			continue
		}
		os.MkdirAll(filepath.Dir(target), 0755)
		src, err := f.Open()
		if err != nil {
			return err
		}
		out, err := os.OpenFile(target, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
		if err != nil {
			src.Close()
			return err
		}
		if _, err := io.Copy(out, src); err != nil {
			src.Close()
			out.Close()
			return err
		}
		src.Close()
		out.Close()
	}
	return nil
}
