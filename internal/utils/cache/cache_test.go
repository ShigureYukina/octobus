package cache

import (
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCache_SetGet(t *testing.T) {
	c := New[string, string](16)
	c.Set("hello", "world")
	v, ok := c.Get("hello")
	assert.True(t, ok)
	assert.Equal(t, "world", v)
}

func TestCache_GetNotFound(t *testing.T) {
	c := New[string, string](16)
	v, ok := c.Get("missing")
	assert.False(t, ok)
	assert.Equal(t, "", v)
}

func TestCache_SetOverwrite(t *testing.T) {
	c := New[string, string](16)
	c.Set("key", "first")
	c.Set("key", "second")
	v, ok := c.Get("key")
	assert.True(t, ok)
	assert.Equal(t, "second", v)
}

func TestCache_Del(t *testing.T) {
	c := New[string, string](16)
	c.Set("a", "1")
	c.Set("b", "2")
	c.Set("c", "3")
	deleted := c.Del("a")
	assert.Equal(t, 1, deleted)
	assert.Equal(t, 2, c.Len())
	_, ok := c.Get("a")
	assert.False(t, ok)
}

func TestCache_DelNotFound(t *testing.T) {
	c := New[string, string](16)
	deleted := c.Del("nonexistent")
	assert.Equal(t, 0, deleted)
}

// 上游 Cache 没有 Exists 方法，使用 Get 的布尔返回值判断存在性。
func TestCache_Exists(t *testing.T) {
	c := New[string, string](16)
	c.Set("x", "1")
	c.Set("y", "2")
	_, okX := c.Get("x")
	_, okY := c.Get("y")
	_, okZ := c.Get("z")
	assert.True(t, okX)
	assert.True(t, okY)
	assert.False(t, okZ)
}

func TestCache_GetAll(t *testing.T) {
	c := New[string, int](16)
	c.Set("a", 1)
	c.Set("b", 2)
	c.Set("c", 3)
	all := c.GetAll()
	assert.Equal(t, map[string]int{"a": 1, "b": 2, "c": 3}, all)
}

func TestCache_Clear(t *testing.T) {
	c := New[string, string](16)
	c.Set("a", "1")
	c.Set("b", "2")
	c.Clear()
	assert.Equal(t, 0, c.Len())
	_, ok := c.Get("a")
	assert.False(t, ok)
}

func TestCache_Len(t *testing.T) {
	c := New[string, string](16)
	assert.Equal(t, 0, c.Len())
	c.Set("1", "a")
	c.Set("2", "b")
	c.Set("3", "c")
	assert.Equal(t, 3, c.Len())
}

func TestCache_ZeroShards(t *testing.T) {
	c := New[string, string](0)
	c.Set("key", "value")
	v, ok := c.Get("key")
	assert.True(t, ok)
	assert.Equal(t, "value", v)
	assert.Equal(t, 1, c.Len())
}

func TestCache_ConcurrentSetGet(t *testing.T) {
	c := New[int, int](64)
	var wg sync.WaitGroup
	for i := range 1000 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			c.Set(i, i)
		}()
	}
	wg.Wait()
	for i := range 1000 {
		v, ok := c.Get(i)
		assert.True(t, ok)
		assert.Equal(t, i, v)
	}
	assert.Equal(t, 1000, c.Len())
}

func TestCache_StringIntType(t *testing.T) {
	c := New[string, int](16)
	c.Set("one", 1)
	c.Set("two", 2)
	v, ok := c.Get("one")
	assert.True(t, ok)
	assert.Equal(t, 1, v)
	all := c.GetAll()
	assert.Equal(t, 2, len(all))
}

func TestCache_IntStringType(t *testing.T) {
	c := New[int, string](16)
	c.Set(1, "one")
	c.Set(2, "two")
	v, ok := c.Get(2)
	assert.True(t, ok)
	assert.Equal(t, "two", v)
	all := c.GetAll()
	assert.Equal(t, map[int]string{1: "one", 2: "two"}, all)
}
