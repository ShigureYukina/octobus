package price

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestGetLLMPrice_KnownModel(t *testing.T) {
	p := GetLLMPrice("gpt-4o")
	assert.NotNil(t, p)
	assert.Equal(t, 2.5, p.Input)
	assert.Equal(t, 10.0, p.Output)
}

// 上游约定调用方传入已规范化的小写模型名（见 GetLLMPrice 注释），
// 这里验证带前缀的规范化名称仍能通过分段匹配命中价格。
func TestGetLLMPrice_PrefixedModel(t *testing.T) {
	p := GetLLMPrice("azure/gpt-4o")
	assert.NotNil(t, p)
	assert.Equal(t, 2.5, p.Input)
	assert.Equal(t, 10.0, p.Output)
}

func TestGetLLMPrice_UnknownModel(t *testing.T) {
	p := GetLLMPrice("nonexistent-model")
	assert.Nil(t, p)
}

func TestGetLLMPrice_AnotherKnownModel(t *testing.T) {
	p := GetLLMPrice("claude-sonnet-4-5-20250929")
	assert.NotNil(t, p)
	assert.Equal(t, 3.0, p.Input)
	assert.Equal(t, 15.0, p.Output)
}
