package diff

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDiff_EmptyBoth(t *testing.T) {
	deleted, added := Diff[int](nil, nil)
	assert.Nil(t, deleted)
	assert.Nil(t, added)
}

func TestDiff_EmptyOld(t *testing.T) {
	deleted, added := Diff[int](nil, []int{1, 2, 3})
	assert.Nil(t, deleted)
	assert.Equal(t, []int{1, 2, 3}, added)
}

func TestDiff_EmptyNew(t *testing.T) {
	deleted, added := Diff[int]([]int{1, 2, 3}, nil)
	assert.ElementsMatch(t, []int{1, 2, 3}, deleted)
	assert.Nil(t, added)
}

func TestDiff_Identical(t *testing.T) {
	deleted, added := Diff[int]([]int{1, 2, 3}, []int{1, 2, 3})
	assert.Nil(t, deleted)
	assert.Nil(t, added)
}

func TestDiff_AddAndDelete(t *testing.T) {
	deleted, added := Diff[int]([]int{1, 2, 3}, []int{2, 3, 4})
	assert.Equal(t, []int{1}, deleted)
	assert.Equal(t, []int{4}, added)
}

func TestDiff_Duplicates(t *testing.T) {
	deleted, added := Diff[int]([]int{1, 1, 2}, []int{1, 2, 2})
	assert.Equal(t, []int{1}, deleted)
	assert.Equal(t, []int{2}, added)
}

func TestDiff_Strings(t *testing.T) {
	deleted, added := Diff[string]([]string{"a", "b"}, []string{"b", "c"})
	assert.Equal(t, []string{"a"}, deleted)
	assert.Equal(t, []string{"c"}, added)
}
