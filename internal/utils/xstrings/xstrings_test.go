package xstrings

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestSplitTrimCompact_BasicSplit(t *testing.T) {
	result := SplitTrimCompact(",", "a,b,c")
	assert.Equal(t, []string{"a", "b", "c"}, result)
}

func TestSplitTrimCompact_Whitespace(t *testing.T) {
	result := SplitTrimCompact(",", "a, b , c")
	assert.Equal(t, []string{"a", "b", "c"}, result)
}

func TestSplitTrimCompact_EmptyItems(t *testing.T) {
	result := SplitTrimCompact(",", "a,,b, ,c")
	assert.Equal(t, []string{"a", "b", "c"}, result)
}

func TestSplitTrimCompact_MultipleParts(t *testing.T) {
	result := SplitTrimCompact(",", "a,b", "c,d")
	assert.Equal(t, []string{"a", "b", "c", "d"}, result)
}

func TestSplitTrimCompact_EmptyInput(t *testing.T) {
	result := SplitTrimCompact(",", "", "")
	assert.Empty(t, result)
}

func TestTrimCompact_Basic(t *testing.T) {
	result := TrimCompact([]string{"a", " b ", "c"})
	assert.Equal(t, []string{"a", "b", "c"}, result)
}

func TestTrimCompact_EmptyAndWhitespace(t *testing.T) {
	result := TrimCompact([]string{"a", "", "  ", "c"})
	assert.Equal(t, []string{"a", "c"}, result)
}

func TestTrimCompact_Empty(t *testing.T) {
	result := TrimCompact([]string{})
	assert.Empty(t, result)
}
