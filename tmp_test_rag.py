import urllib.request
import json

url = "http://localhost:8084/api/v1/knowledge-bases/11111/rag"
data = json.dumps({"query": "查询知识库中的问题", "topK": 10}).encode('utf-8')
headers = {
    "Content-Type": "application/json",
    "X-User-Id": "1"
}

try:
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=60) as resp:
        print("Status:", resp.status)
        print(resp.read().decode('utf-8'))
except Exception as e:
    print("Error:", e)
