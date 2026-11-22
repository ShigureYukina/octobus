#!/usr/bin/env bash
# relay-loadtest-setup.sh
# 设置压测环境：创建渠道（指向 mock upstream）和 API Key
# 用法: bash java/scripts/relay-loadtest-setup.sh

set -euo pipefail

BASE="${BASE:-http://localhost:8081}"
MOCK_URL="${MOCK_URL:-http://localhost:8088}"

echo "=== Step 1: 登录 admin ==="
LOGIN=$(curl -s "$BASE/api/v1/user/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')
TOKEN=$(echo "$LOGIN" | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo "")
if [ -z "$TOKEN" ]; then
  echo "登录失败: $LOGIN"
  exit 1
fi
echo "Token: ${TOKEN:0:20}..."

AUTH="Authorization: Bearer $TOKEN"

echo ""
echo "=== Step 2: 创建渠道（指向 mock upstream）==="
CHANNEL=$(curl -s "$BASE/api/v1/channel/create" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "{\"name\":\"mock-loadtest\",\"type\":\"openai\",\"base_url\":\"$MOCK_URL\",\"models\":[\"gpt-4o-mock\"]}")
CHANNEL_ID=$(echo "$CHANNEL" | python -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
echo "渠道ID: $CHANNEL_ID"

echo ""
echo "=== Step 3: 创建 API Key ==="
KEY_RESULT=$(curl -s "$BASE/api/v1/apikey/create" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "{\"name\":\"loadtest-key\",\"remain\":100000,\"models_limit\":[\"gpt-4o-mock\"]}")
API_KEY=$(echo "$KEY_RESULT" | python -c "import sys,json;d=json.load(sys.stdin);print(d.get('data',{}).get('key',''))" 2>/dev/null || echo "")
if [ -z "$API_KEY" ]; then
  echo "创建 API Key 失败: $KEY_RESULT"
  exit 1
fi
echo "API Key: $API_KEY"

echo ""
echo "=== Step 4: 验证 relay 转发 ==="
sleep 1
TEST_RESULT=$(curl -s "$BASE/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $API_KEY" \
  -d '{"model":"gpt-4o-mock","messages":[{"role":"user","content":"ping"}]}')
echo "验证结果: $(echo $TEST_RESULT | python -c "import sys,json;d=json.load(sys.stdin);print(d.get('choices',[{}])[0].get('message',{}).get('content','FAIL'))" 2>/dev/null)"

echo ""
echo "======================================"
echo "压测环境就绪！"
echo "======================================"
echo ""
echo "用 k6 压测:"
echo "  k6 run -e API_KEY=$API_KEY java/scripts/relay-loadtest.js"
echo ""
echo "用 wrk 压测:"
echo "  wrk -t4 -c20 -d30s -s java/scripts/wrk-relay.lua $BASE/v1/chat/completions"
echo "  (需要先创建 wrk-relay.lua)"
echo ""
echo "用 hey 压测:"
echo "  hey -n 5000 -c 30 -m POST \\"
echo "    -H \"Authorization: Bearer $API_KEY\" \\"
echo "    -H \"Content-Type: application/json\" \\"
echo "    -d '{\"model\":\"gpt-4o-mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}' \\"
echo "    $BASE/v1/chat/completions"
echo ""
echo "用 vegeta 压测:"
echo "  echo 'POST $BASE/v1/chat/completions' | vegeta attack \\"
echo "    -header \"Authorization: Bearer $API_KEY\" \\"
echo "    -header \"Content-Type: application/json\" \\"
echo "    -body <(echo '{\"model\":\"gpt-4o-mock\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}') \\"
echo "    -rate 50/s -duration 30s | vegeta report"
