from fastapi.testclient import TestClient


def test_health_is_public(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "officecare-ai-service",
        "version": "0.1.0",
        "environment": "test",
        "supportedFileTypes": ["pdf", "docx", "txt"],
    }
    assert response.headers["X-Trace-Id"]


def test_openapi_contains_parse_endpoint(client: TestClient) -> None:
    response = client.get("/openapi.json")

    assert response.status_code == 200
    assert "/api/v1/internal/documents/parse" in response.json()["paths"]

