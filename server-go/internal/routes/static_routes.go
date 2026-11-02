package routes

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
)

// ResolveStaticDir locates the exported web frontend.
//
// The Next.js app is exported as static files ("web/out") and shipped next to
// the server binary, so a release is one binary plus a folder of assets — no
// Node.js runtime is required on the server.
//
// Two layouts are probed:
//   - "../web/out" : the server runs from "server-go/" (development)
//   - "web/out"    : the server runs from the root of a release package
//
// The WEB_DIST environment variable overrides the lookup.
func ResolveStaticDir() string {
	if dir := strings.TrimSpace(os.Getenv("WEB_DIST")); dir != "" {
		if _, err := os.Stat(filepath.Join(dir, "index.html")); err == nil {
			return dir
		}
	}

	candidates := []string{
		filepath.Join("..", "web", "out"),
		filepath.Join("web", "out"),
		filepath.Join(".", "web", "out"),
	}
	for _, dir := range candidates {
		if _, err := os.Stat(filepath.Join(dir, "index.html")); err == nil {
			return dir
		}
	}
	return ""
}

// SetupStaticRoutes serves the exported frontend from distDir.
//
// The export contains one HTML file per route ("/charts" -> charts.html) plus
// the RSC payload files the client router fetches ("/charts.txt"), so a
// request is resolved as: exact file -> "<path>.html" -> "<path>/index.txt"
// -> the exported 404 page.
func SetupStaticRoutes(router *gin.Engine, distDir string) {
	if distDir == "" {
		return
	}

	notFoundFile := filepath.Join(distDir, "404.html")
	indexFile := filepath.Join(distDir, "index.html")

	router.NoRoute(func(c *gin.Context) {
		path := c.Request.URL.Path

		// Unknown API endpoints must answer with a plain 404 and never the
		// application shell.
		if path == "/api" || strings.HasPrefix(path, "/api/") {
			c.Status(http.StatusNotFound)
			return
		}

		trimmed := strings.TrimSuffix(path, "/")

		// 0) the root path serves the application shell
		if trimmed == "" {
			c.File(indexFile)
			return
		}

		// 1) a real file: /_next/static/..., /charts.txt, /ic_app_icon.svg, ...
		if file, ok := resolveStaticFile(distDir, path); ok {
			c.File(file)
			return
		}

		// 2) a page route: /charts -> charts.html
		if trimmed != "" {
			if file, ok := resolveStaticFile(distDir, trimmed+".html"); ok {
				c.File(file)
				return
			}

			// 3) the RSC payload of a trailing-slash URL: /charts/ -> charts.txt
			if strings.HasSuffix(path, "/") {
				if file, ok := resolveStaticFile(distDir, trimmed+".txt"); ok {
					c.File(file)
					return
				}
			}
		}

		// 4) an unknown route -> the exported 404 page. The body is written
		// through c.Data because http.ServeFile (used by c.File) always emits
		// a 200 and would overwrite the status code set here.
		if content, err := os.ReadFile(notFoundFile); err == nil {
			c.Data(http.StatusNotFound, "text/html; charset=utf-8", content)
			return
		}

		// 5) last resort -> the application shell
		c.File(indexFile)
	})
}

// resolveStaticFile maps a request path to a file inside distDir. It reports
// false when the path escapes the directory or when the file does not exist.
func resolveStaticFile(distDir, urlPath string) (string, bool) {
	// Cleaning an absolute path removes every ".." segment, so a traversal
	// attempt stays inside distDir.
	cleaned := filepath.Clean("/" + strings.TrimPrefix(urlPath, "/"))
	full := filepath.Join(distDir, cleaned)

	absDir, err := filepath.Abs(distDir)
	if err != nil {
		return "", false
	}
	absFull, err := filepath.Abs(full)
	if err != nil {
		return "", false
	}
	if absFull != absDir && !strings.HasPrefix(absFull, absDir+string(os.PathSeparator)) {
		return "", false
	}

	info, err := os.Stat(absFull)
	if err != nil || info.IsDir() {
		return "", false
	}
	return absFull, true
}
