from app.core.config import Settings
from app.schemas.rag import RagAnswerRequest
from app.services.rag_retrieval import MilvusEvidenceRetriever


class FakeEmbeddingProvider:
    model_name = "test-embedding"
    dimension = 3

    def __init__(self) -> None:
        self.query = ""

    def embed_query(self, text: str) -> list[float]:
        self.query = text
        return [0.1, 0.2, 0.3]


def _entity(
    *,
    chunk_index: int,
    text: str,
    page_number: int = 8,
    heading: str = "卡纸处理",
) -> dict:
    return {
        "knowledge_base_id": 1,
        "brand_id": 10,
        "model_id": 20,
        "document_id": 30,
        "document_version_id": 40,
        "document_title": "维修手册",
        "original_file_name": "manual.pdf",
        "page_number": page_number,
        "chunk_index": chunk_index,
        "heading": heading,
        "text": text,
    }


class FakeMilvusClient:
    def __init__(
        self,
        *,
        search_hits: list[dict] | None = None,
        query_records: list[dict] | None = None,
    ) -> None:
        self.loaded_collection: str | None = None
        self.search_arguments: dict = {}
        self.query_arguments: dict = {}
        self.search_hits = search_hits or []
        self.query_records = query_records or []

    def has_collection(self, **kwargs) -> bool:
        return True

    def load_collection(self, **kwargs) -> None:
        self.loaded_collection = kwargs["collection_name"]

    def search(self, **kwargs) -> list[list[dict]]:
        self.search_arguments = kwargs
        return [self.search_hits]

    def query(self, **kwargs) -> list[dict]:
        self.query_arguments = kwargs
        return self.query_records


def _hit(distance: float, entity: dict) -> dict:
    return {"distance": distance, "entity": entity}


def _request(question: str = "打印机卡纸后如何处理？") -> RagAnswerRequest:
    return RagAnswerRequest(
        question=question,
        knowledge_base_id=1,
        brand_id=10,
        model_id=20,
        brand_name="Brother",
        model_name="DCP-L2550DW",
        document_version_ids=[40, 41],
        top_k=5,
        score_threshold=0.45,
    )


def _settings(**overrides) -> Settings:
    values = {
        "_env_file": None,
        "embedding_base_url": "http://embedding.test/v1",
        "embedding_model": "test-embedding",
        "embedding_dimension": 3,
        "milvus_uri": "http://milvus.test:19530",
        "milvus_collection": "officecare_test_chunks",
        "rag_dense_candidate_limit": 12,
        "rag_lexical_scan_limit": 100,
        "rag_lexical_candidate_limit": 20,
        "rag_lexical_score_threshold": 0.18,
        "rag_neighbor_window": 2,
    }
    values.update(overrides)
    return Settings(**values)


def test_retriever_applies_java_approved_versions_and_score_threshold() -> None:
    provider = FakeEmbeddingProvider()
    client = FakeMilvusClient(
        search_hits=[
            _hit(0.91, _entity(chunk_index=2, text="关闭电源，然后取出卡纸。")),
            _hit(0.40, _entity(chunk_index=3, text="这一条低于阈值。")),
        ]
    )
    retriever = MilvusEvidenceRetriever(
        _settings(),
        provider,
        client_factory=lambda: client,
    )

    evidence = retriever.search(_request())

    assert len(evidence) == 1
    assert evidence[0].score == 0.91
    assert evidence[0].document.metadata["page_number"] == 8
    assert evidence[0].document.page_content.startswith("关闭电源")
    assert provider.query == "打印机卡纸后如何处理？"
    assert client.loaded_collection == "officecare_test_chunks"
    assert client.search_arguments["data"] == [[0.1, 0.2, 0.3]]
    assert client.search_arguments["limit"] == 12
    assert client.search_arguments["filter"] == (
        "knowledge_base_id == 1 and brand_id == 10 and model_id == 20 "
        "and document_version_id in [40, 41]"
    )
    assert client.query_arguments["limit"] == 100


def test_retriever_uses_navigation_hit_to_add_neighboring_body_chunks() -> None:
    navigation = _entity(
        chunk_index=10,
        text="相关信息\n\n主页 > 故障排除 > 卡纸 > 设备内部卡纸",
        page_number=395,
        heading="相关信息",
    )
    body_one = _entity(
        chunk_index=11,
        text="打开前盖，取出墨粉盒和硒鼓单元组件。",
        page_number=395,
        heading="设备内部卡纸",
    )
    body_two = _entity(
        chunk_index=12,
        text="等待内部零件冷却，然后慢慢拉出卡住的纸张。",
        page_number=395,
        heading="设备内部卡纸",
    )
    client = FakeMilvusClient(
        search_hits=[_hit(0.90, navigation)],
        query_records=[navigation, body_one, body_two],
    )
    retriever = MilvusEvidenceRetriever(
        _settings(rag_lexical_score_threshold=1.0),
        FakeEmbeddingProvider(),
        client_factory=lambda: client,
    )

    evidence = retriever.search(_request("设备内部卡纸应该怎么处理？"))

    assert [item.document.metadata["chunk_index"] for item in evidence] == [11, 12]
    assert all("主页 >" not in item.document.page_content for item in evidence)
    assert evidence[0].score > evidence[1].score


def test_retriever_recovers_exact_numeric_evidence_with_lexical_scan() -> None:
    unrelated_hits = [
        _entity(
            chunk_index=index,
            text=f"打印机可以通过无线网络连接，网络设置步骤{index}。",
            page_number=100 + index,
            heading="网络设置",
        )
        for index in range(2, 8)
    ]
    exact_evidence = _entity(
        chunk_index=20,
        text=(
            "如果打印页面每隔94毫米出现黑色或白色斑点，请打印十到十五张"
            "空白纸，然后清洁硒鼓单元。"
        ),
        page_number=403,
        heading="提高打印质量",
    )
    client = FakeMilvusClient(
        search_hits=[
            _hit(0.90 - index * 0.01, entity)
            for index, entity in enumerate(unrelated_hits)
        ],
        query_records=[*unrelated_hits, exact_evidence],
    )
    retriever = MilvusEvidenceRetriever(
        _settings(),
        FakeEmbeddingProvider(),
        client_factory=lambda: client,
    )

    evidence = retriever.search(_request("打印页面每隔94毫米出现重复斑点怎么办？"))

    assert evidence[0].document.metadata["chunk_index"] == 20
    assert "94毫米" in evidence[0].document.page_content
    assert evidence[0].score >= 0.18


def test_retriever_falls_back_to_dense_when_lexical_scan_fails() -> None:
    class QueryFailureClient(FakeMilvusClient):
        def query(self, **kwargs) -> list[dict]:
            raise RuntimeError("query unavailable")

    client = QueryFailureClient(
        search_hits=[
            _hit(0.88, _entity(chunk_index=4, text="按步骤取出卡住的纸张。"))
        ]
    )
    retriever = MilvusEvidenceRetriever(
        _settings(),
        FakeEmbeddingProvider(),
        client_factory=lambda: client,
    )

    evidence = retriever.search(_request())

    assert len(evidence) == 1
    assert evidence[0].score == 0.88


def test_retriever_filters_explicit_other_model_before_hybrid_ranking() -> None:
    other_model = _entity(
        chunk_index=30,
        text="相关型号: MFC-7895DW\n按[设置]进入传真和设置锁定菜单。",
        page_number=295,
        heading="设置锁定密码",
    )
    selected_model = _entity(
        chunk_index=31,
        text="相关型号: DCP-L2550DW\n打开网络基本管理并设置新密码。",
        page_number=511,
        heading="设置网络基本管理的登录密码",
    )
    shared = _entity(
        chunk_index=32,
        text="设备的默认密码位于背面带有Pwd字样的标签上。",
        page_number=1,
        heading="安全网络访问",
    )
    client = FakeMilvusClient(
        search_hits=[
            _hit(0.95, other_model),
            _hit(0.90, selected_model),
            _hit(0.85, shared),
        ],
        query_records=[other_model, selected_model, shared],
    )
    retriever = MilvusEvidenceRetriever(
        _settings(),
        FakeEmbeddingProvider(),
        client_factory=lambda: client,
    )

    evidence = retriever.search(_request("如何修改设备的默认管理密码？"))

    chunk_indexes = [item.document.metadata["chunk_index"] for item in evidence]
    assert 30 not in chunk_indexes
    assert set(chunk_indexes) == {31, 32}
    assert len(chunk_indexes) == 2
    assert all("MFC-7895DW" not in item.document.page_content for item in evidence)


def test_retriever_uses_scope_free_query_to_recover_pc_fax_heading() -> None:
    unrelated = [
        _entity(
            chunk_index=index,
            text=(
                "相关型号: MFC-7895DW\n"
                f"设备设置和传真接收说明{index}。"
            ),
            page_number=100 + index,
            heading="传真设置",
        )
        for index in range(40, 48)
    ]
    pc_fax_send = _entity(
        chunk_index=60,
        text=(
            "相关型号: MFC-7895DW\n"
            "在应用程序中选择打印命令，选择Brother PC-FAX作为打印机，"
            "输入传真号码并开始发送。"
        ),
        page_number=239,
        heading="使用 PC-FAX 发送传真 (Windows)",
    )
    address_book = _entity(
        chunk_index=61,
        text=(
            "相关型号: MFC-7895DW\n"
            "在PC-FAX地址簿中添加成员，以便以后选择传真接收方。"
        ),
        page_number=229,
        heading="添加成员到 PC-FAX 地址簿 (Windows)",
    )
    long_navigation = _entity(
        chunk_index=62,
        text="PC-FAX 发送传真\n" + "目录项目 " * 200,
        page_number=4,
        heading="主页 > 目录",
    )
    client = FakeMilvusClient(
        search_hits=[
            _hit(0.90 - index * 0.01, entity)
            for index, entity in enumerate(unrelated)
        ],
        query_records=[*unrelated, address_book, long_navigation, pc_fax_send],
    )
    provider = FakeEmbeddingProvider()
    retriever = MilvusEvidenceRetriever(
        _settings(),
        provider,
        client_factory=lambda: client,
    )
    request = RagAnswerRequest(
        question="Brother MFC-7895DW如何在Windows中使用PC-FAX发送传真？",
        knowledge_base_id=1,
        brand_id=10,
        model_id=20,
        brand_name="Brother",
        model_name="MFC-7895DW",
        document_version_ids=[40],
        top_k=5,
        score_threshold=0.45,
    )

    evidence = retriever.search(request)

    assert provider.query == "如何在Windows中使用PC-FAX发送传真？"
    assert evidence[0].document.metadata["chunk_index"] == 60
    assert evidence[0].document.metadata["heading"] == (
        "使用 PC-FAX 发送传真 (Windows)"
    )
    assert all(item.document.metadata["chunk_index"] != 62 for item in evidence)


def test_retriever_returns_empty_when_collection_does_not_exist() -> None:
    class MissingCollectionClient(FakeMilvusClient):
        def has_collection(self, **kwargs) -> bool:
            return False

    retriever = MilvusEvidenceRetriever(
        _settings(),
        FakeEmbeddingProvider(),
        client_factory=MissingCollectionClient,
    )

    assert retriever.search(_request()) == []
