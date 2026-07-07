import urllib.request
import json

# 测试 Qdrant 集合信息
base = "http://localhost:6333"

for name in ["ai-agent_11111", "AI-agent_11111"]:
    url = f"{base}/collections/{name}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"Collection {name} exists:")
            print(json.dumps(json.loads(resp.read().decode('utf-8')), indent=2, ensure_ascii=False))
    except Exception as e:
        print(f"Collection {name} error: {e}")

# 尝试 scroll points
for name in ["ai-agent_11111", "AI-agent_11111"]:
    url = f"{base}/collections/{name}/points/scroll"
    data = json.dumps({"limit": 10, "with_payload": True, "with_vector": False}).encode('utf-8')
    try:
        req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"\nPoints in {name}:")
            print(json.dumps(json.loads(resp.read().decode('utf-8')), indent=2, ensure_ascii=False))
    except Exception as e:
        print(f"\nPoints {name} error: {e}")
