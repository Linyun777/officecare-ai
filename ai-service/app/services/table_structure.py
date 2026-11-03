"""Conservative table adapters; legacy flattened text is produced elsewhere."""

from html.parser import HTMLParser

from app.schemas.document import DocumentTable, TableCell, TableRow


class _TableParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.rows: list[TableRow] = []
        self.cells: list[TableCell] | None = None
        self.cell: dict | None = None
        self.parts: list[str] = []
        self.occupied: set[tuple[int, int]] = set()
        self.depth = 0
        self.ignored = 0

    def handle_starttag(self, tag, attributes):
        tag = tag.lower()
        if tag in {"script", "style"}:
            self.ignored += 1
            return
        if self.ignored:
            return
        if tag == "table":
            self.depth += 1
            if self.depth > 1:
                raise ValueError("nested tables have no verified flat layout")
        elif tag == "tr":
            if self.cells is not None:
                raise ValueError("unclosed table row")
            self.cells = []
        elif tag in {"td", "th"}:
            if self.cells is None or self.cell is not None:
                raise ValueError("cell outside a row or unclosed cell")
            attrs = dict(attributes)
            row_span = int(attrs.get("rowspan", "1"))
            column_span = int(attrs.get("colspan", "1"))
            if not 1 <= row_span <= 1000 or not 1 <= column_span <= 1000:
                raise ValueError("unsupported table span")
            column = 0
            row = len(self.rows)
            while (row, column) in self.occupied:
                column += 1
            if len(self.occupied) + row_span * column_span > 20000:
                raise ValueError("table cell budget exceeded")
            for target_row in range(row, row + row_span):
                for target_column in range(column, column + column_span):
                    position = (target_row, target_column)
                    if position in self.occupied:
                        raise ValueError("overlapping table spans")
                    self.occupied.add(position)
            self.cell = {
                "column_index": column,
                "row_span": row_span,
                "column_span": column_span,
                "header": tag == "th",
            }
            self.parts = []
        elif tag == "br" and self.cell is not None:
            self.parts.append("\n")

    def handle_data(self, value):
        if self.cell is not None and not self.ignored:
            self.parts.append(value)

    def handle_endtag(self, tag):
        tag = tag.lower()
        if tag in {"script", "style"}:
            self.ignored = max(0, self.ignored - 1)
            return
        if self.ignored:
            return
        if tag in {"td", "th"} and self.cell is not None:
            text = " ".join("".join(self.parts).split())
            self.cells.append(TableCell(text=text, **self.cell))
            self.cell = None
        elif tag == "tr":
            if self.cells is None or self.cell is not None:
                raise ValueError("incomplete table row")
            self.rows.append(TableRow(row_index=len(self.rows), cells=self.cells))
            self.cells = None
        elif tag == "table":
            self.depth -= 1


def html_table(value: str, *, captions=None, footnotes=None) -> DocumentTable:
    """Return unavailable structure instead of guessing malformed HTML layout."""
    try:
        parser = _TableParser()
        parser.feed(value)
        parser.close()
        if parser.cell is not None or parser.cells is not None or not parser.rows:
            raise ValueError("no complete HTML table")
        return DocumentTable(
            rows=parser.rows,
            captions=captions or [],
            footnotes=footnotes or [],
        )
    except (ValueError, TypeError):
        return DocumentTable(
            captions=captions or [],
            footnotes=footnotes or [],
            warnings=["表格布局无法确认，保留原文，不推测合并关系。"],
        )


def docx_table(table) -> DocumentTable:
    from docx.oxml.ns import qn

    rows: list[TableRow] = []
    originals: dict[object, TableCell] = {}
    for row_index, row in enumerate(table.rows):
        cells = []
        seen = set()
        row_cells = row.cells
        header_node = row._tr.find("./" + qn("w:trPr") + "/" + qn("w:tblHeader"))
        header = header_node is not None and header_node.get(qn("w:val")) not in {
            "0", "false", "off",
        }
        offset = row.grid_cols_before
        for index, cell in enumerate(row_cells):
            key = cell._tc
            if key in seen:
                continue
            seen.add(key)
            if key in originals:
                originals[key].row_span += 1
                continue
            value = TableCell(
                column_index=offset + index,
                text=" ".join(cell.text.split()),
                column_span=sum(item._tc is key for item in row_cells),
                header=header,
            )
            cells.append(value)
            originals[key] = value
        rows.append(TableRow(row_index=row_index, cells=cells))
    return DocumentTable(rows=rows)
