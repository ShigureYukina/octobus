package auth

import (
	"testing"

	"github.com/bestruirui/octopus/internal/conf"
	"github.com/stretchr/testify/assert"
)

// 上游 JWT 密钥来自 op.UserGet()（用户名+密码），不再使用 conf.AppConfig.JwtSecret。
// 这里只做生成/校验的往返测试，不依赖具体密钥值。

func TestGenerateJWTToken_DefaultExpiry(t *testing.T) {
	token, expiresAt, err := GenerateJWTToken(0)
	assert.NoError(t, err)
	assert.NotEmpty(t, token)
	assert.Equal(t, 900, expiresAt)
	assert.True(t, VerifyJWTToken(token))
}

func TestGenerateJWTToken_CustomExpiry(t *testing.T) {
	token, expiresAt, err := GenerateJWTToken(60)
	assert.NoError(t, err)
	assert.NotEmpty(t, token)
	assert.Equal(t, 60, expiresAt)
	assert.True(t, VerifyJWTToken(token))
}

func TestGenerateJWTToken_LongExpiry(t *testing.T) {
	token, expiresAt, err := GenerateJWTToken(-1)
	assert.NoError(t, err)
	assert.NotEmpty(t, token)
	assert.Equal(t, 30*24*3600, expiresAt)
	assert.True(t, VerifyJWTToken(token))
}

func TestVerifyJWTToken_Valid(t *testing.T) {
	token, _, err := GenerateJWTToken(15)
	assert.NoError(t, err)
	assert.True(t, VerifyJWTToken(token))
}

func TestVerifyJWTToken_InvalidToken(t *testing.T) {
	assert.False(t, VerifyJWTToken("invalid.token.string"))
}

func TestVerifyJWTToken_EmptyToken(t *testing.T) {
	assert.False(t, VerifyJWTToken(""))
}

func TestGenerateAPIKey_Format(t *testing.T) {
	key := GenerateAPIKey()
	expectedPrefix := "sk-" + conf.APP_NAME + "-"
	assert.True(t, len(key) == len(expectedPrefix)+48, "expected total length %d, got %d", len(expectedPrefix)+48, len(key))
	assert.Equal(t, expectedPrefix, key[:len(expectedPrefix)])
}

func TestGenerateAPIKey_Uniqueness(t *testing.T) {
	keys := make(map[string]struct{})
	for i := 0; i < 10; i++ {
		key := GenerateAPIKey()
		keys[key] = struct{}{}
	}
	assert.Equal(t, 10, len(keys), "all generated API keys should be unique")
}
