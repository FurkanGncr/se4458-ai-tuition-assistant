from policy_mcp.chunking import chunk_file, chunk_markdown

EXPECTED_SECTIONS = [
    "1",
    "2.1", "2.2", "2.3",
    "3.1", "3.2", "3.3", "3.4",
    "4.1", "4.2", "4.3",
    "5.1", "5.2",
    "6.1", "6.2", "6.3",
    "7.1", "7.2", "7.3", "7.4",
    "8.1", "8.2", "8.3", "8.4",
    "9",
]


def test_policy_document_splits_into_one_chunk_per_section(policy_doc):
    chunks = chunk_file(policy_doc)

    assert [c.section for c in chunks] == EXPECTED_SECTIONS
    assert all(c.text for c in chunks), "every chunk must have body text"


def test_chunks_keep_document_and_parent_metadata(policy_doc):
    by_section = {c.section: c for c in chunk_file(policy_doc)}

    installments = by_section["3.2"]
    assert installments.heading == "Number and size of installments"
    assert installments.parent_section == "3"
    assert installments.parent_heading == "Installment policy"
    assert installments.document == "University_Tuition_Policies.md"
    assert installments.document_id == "UNI-TUITION-2026-01"
    assert "up to three installments" in installments.text
    assert "**" not in installments.text  # Markdown bold markers are removed


def test_section_without_subsections_is_its_own_chunk(policy_doc):
    by_section = {c.section: c for c in chunk_file(policy_doc)}

    scope = by_section["1"]
    assert scope.heading == "Scope and terminology"
    assert scope.parent_heading is None
    assert "Outstanding balance" in scope.text


def test_headings_inside_code_blocks_are_not_split():
    markdown = "\n".join(
        [
            "**Document ID:** DOC-1",
            "## 1. Example",
            "```",
            "## 2. Not a heading",
            "```",
            "after the fence",
        ]
    )

    chunks = chunk_markdown(markdown, document="x.md")

    assert [c.section for c in chunks] == ["1"]
    assert "## 2. Not a heading" in chunks[0].text
    assert chunks[0].document_id == "DOC-1"


def test_section_9_keeps_its_json_example(policy_doc):
    guidance = chunk_file(policy_doc)[-1]

    assert guidance.section == "9"
    assert '"section": "3.2"' in guidance.text
