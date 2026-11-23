package conf

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/spf13/viper"
	"github.com/stretchr/testify/assert"
)

// 上游 conf 包移除了 fork 的 validateConfig，配置加载改为 viper 默认值 + Load。
// 这里改为覆盖上游实际存在的默认配置与文件加载行为。

func TestSetDefaults(t *testing.T) {
	viper.Reset()
	defer viper.Reset()

	setDefaults()

	assert.Equal(t, "0.0.0.0", viper.GetString("server.host"))
	assert.Equal(t, 8080, viper.GetInt("server.port"))
	assert.Equal(t, "sqlite", viper.GetString("database.type"))
	assert.Equal(t, "data/data.db", viper.GetString("database.path"))
	assert.Equal(t, "info", viper.GetString("log.level"))
}

func TestLoad_CustomConfigFile(t *testing.T) {
	viper.Reset()
	defer viper.Reset()

	dir := t.TempDir()
	configPath := filepath.Join(dir, "config.json")
	content := `{
		"server": {"host": "127.0.0.1", "port": 9090},
		"database": {"type": "sqlite", "path": "data/custom.db"},
		"log": {"level": "debug"}
	}`
	assert.NoError(t, os.WriteFile(configPath, []byte(content), 0o644))

	orig := AppConfig
	defer func() { AppConfig = orig }()

	assert.NoError(t, Load(configPath))
	assert.Equal(t, "127.0.0.1", AppConfig.Server.Host)
	assert.Equal(t, 9090, AppConfig.Server.Port)
	assert.Equal(t, "sqlite", AppConfig.Database.Type)
	assert.Equal(t, "data/custom.db", AppConfig.Database.Path)
	assert.Equal(t, "debug", AppConfig.Log.Level)
}

func TestLoad_DefaultsWhenFieldsMissing(t *testing.T) {
	viper.Reset()
	defer viper.Reset()

	dir := t.TempDir()
	configPath := filepath.Join(dir, "config.json")
	assert.NoError(t, os.WriteFile(configPath, []byte(`{"server": {"port": 7777}}`), 0o644))

	orig := AppConfig
	defer func() { AppConfig = orig }()

	assert.NoError(t, Load(configPath))
	assert.Equal(t, 7777, AppConfig.Server.Port)
	assert.Equal(t, "0.0.0.0", AppConfig.Server.Host)
	assert.Equal(t, "sqlite", AppConfig.Database.Type)
	assert.Equal(t, "data/data.db", AppConfig.Database.Path)
	assert.Equal(t, "info", AppConfig.Log.Level)
}
