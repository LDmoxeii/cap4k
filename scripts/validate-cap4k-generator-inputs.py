import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ERROR = "ERROR"
WARN = "WARN"
RECOVERY_HINT = "RECOVERY_HINT"


@dataclass
class Issue:
    level: str
    file: str
    path: str
    message: str
    hint: str = ""


def add_issue(
    issues: list[Issue],
    level: str,
    file: Path,
    path: str,
    message: str,
    hint: str = "",
) -> None:
    issues.append(
        Issue(level=level, file=str(file), path=path, message=message, hint=hint)
    )


def read_json_file(file: Path, issues: list[Issue]) -> Any | None:
    try:
        return json.loads(file.read_text(encoding="utf-8"))
    except FileNotFoundError:
        add_issue(issues, ERROR, file, "$", "file does not exist")
    except json.JSONDecodeError as exc:
        add_issue(
            issues,
            ERROR,
            file,
            "$",
            f"invalid JSON: {exc.msg}",
            f"line {exc.lineno}, column {exc.colno}",
        )
    return None


def issue_to_dict(issue: Issue) -> dict[str, str]:
    return {
        "level": issue.level,
        "file": issue.file,
        "path": issue.path,
        "message": issue.message,
        "hint": issue.hint,
    }


def emit_output(issues: list[Issue], json_output: bool) -> None:
    if json_output:
        print(
            json.dumps(
                [issue_to_dict(issue) for issue in issues],
                ensure_ascii=False,
                indent=2,
            )
        )
        return

    if not issues:
        print("OK: no issues found.")
        return

    for issue in issues:
        location = f"{issue.file}:{issue.path}" if issue.path else issue.file
        print(f"{issue.level}: {location}: {issue.message}")
        if issue.hint:
            print(f"  hint: {issue.hint}")


DESIGN_TAGS = {
    "command",
    "query",
    "client",
    "api_payload",
    "domain_event",
    "integration_event",
    "domain_service",
    "saga",
}
DESIGN_PUBLIC_FIELDS = {
    "tag",
    "name",
    "package",
    "description",
    "aggregates",
    "fields",
    "resultFields",
    "eventName",
    "persist",
    "artifacts",
}
DESIGN_REMOVED_FIELDS = {
    "desc",
    "requestFields",
    "responseFields",
    "traits",
    "role",
    "scope",
    "entity",
}
RESULT_FIELD_TAGS = {"query", "client", "api_payload"}
EVENT_NAME_TAGS = {"domain_event", "integration_event"}
SELF_TOKEN_RE = re.compile(r"(^|[^A-Za-z0-9_])self([^A-Za-z0-9_]|$)", re.IGNORECASE)


MYSQL_COMMENT_RE = re.compile(r"comment\s*=?\s*'((?:''|[^'])*)'", re.IGNORECASE)
COMMENT_ON_TABLE_RE = re.compile(
    r"comment\s+on\s+table\s+[\w.\"]+\s+is\s+'((?:''|[^'])*)'",
    re.IGNORECASE,
)
COMMENT_ON_COLUMN_RE = re.compile(
    r"comment\s+on\s+column\s+[\w.\"]+\s+is\s+'((?:''|[^'])*)'",
    re.IGNORECASE,
)
ANNOTATION_RE = re.compile(r"@([A-Za-z][A-Za-z0-9_]*)(=([^;\s]*))?")
TABLE_ANNOTATIONS = {
    "Parent",
    "P",
    "AggregateRoot",
    "Root",
    "R",
    "ValueObject",
    "VO",
    "Ignore",
    "I",
    "DynamicInsert",
    "DynamicUpdate",
}
COLUMN_ANNOTATIONS = {
    "T",
    "TYPE",
    "E",
    "ENUM",
    "RefId",
    "Deleted",
    "Version",
    "GeneratedValue",
    "Managed",
    "Inherited",
    "Reference",
    "Ref",
    "Relation",
    "Rel",
    "Lazy",
    "L",
    "Count",
    "C",
    "RefAggregate",
}
REJECTED_TABLE_ANNOTATIONS = {"IdGenerator", "IG", "SoftDeleteColumn"}
REJECTED_COLUMN_ANNOTATIONS = {"Exposed", "Insertable", "Updatable"}
PRESENCE_ANNOTATIONS = {
    "ValueObject",
    "VO",
    "Ignore",
    "I",
    "Deleted",
    "Version",
    "Managed",
    "Inherited",
}
BOOLEAN_ANNOTATIONS = {
    "AggregateRoot",
    "Root",
    "R",
    "DynamicInsert",
    "DynamicUpdate",
    "Lazy",
    "L",
}
REQUIRED_VALUE_ANNOTATIONS = {
    "Parent",
    "P",
    "T",
    "TYPE",
    "E",
    "ENUM",
    "RefId",
    "Reference",
    "Ref",
    "Relation",
    "Rel",
    "Count",
    "C",
    "RefAggregate",
}
RELATION_TYPES = {"MANY_TO_ONE", "ONE_TO_ONE", "1:1", "*:1", "MANYTOONE", "ONETOONE"}


def is_nonblank_string(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate_field_array(
    file: Path,
    issues: list[Issue],
    entry_name: str,
    value: Any,
    path: str,
    *,
    domain_event: bool,
) -> None:
    if value is None:
        return
    if not isinstance(value, list):
        add_issue(issues, ERROR, file, path, "must be an array")
        return

    for index, field in enumerate(value):
        field_path = f"{path}[{index}]"
        if not isinstance(field, dict):
            add_issue(issues, ERROR, file, field_path, "field item must be an object")
            continue

        name = field.get("name")
        field_type = field.get("type")
        if not is_nonblank_string(name):
            add_issue(issues, ERROR, file, f"{field_path}.name", "field name is required")
        if domain_event and name == "entity":
            add_issue(
                issues,
                ERROR,
                file,
                f"{field_path}.name",
                f"domain_event {entry_name} field 'entity' is reserved",
            )
        if not is_nonblank_string(field_type):
            add_issue(issues, ERROR, file, f"{field_path}.type", "field type is required")
        elif SELF_TOKEN_RE.search(field_type):
            add_issue(
                issues,
                ERROR,
                file,
                f"{field_path}.type",
                "field type must not use self",
                "Use an explicit type name.",
            )


def validate_design(file: Path, issues: list[Issue]) -> None:
    data = read_json_file(file, issues)
    if data is None:
        return
    if not isinstance(data, list):
        add_issue(issues, ERROR, file, "$", "design JSON root must be an array")
        return

    for index, entry in enumerate(data):
        path = f"$[{index}]"
        if not isinstance(entry, dict):
            add_issue(issues, ERROR, file, path, "design entry must be an object")
            continue

        tag = entry.get("tag")
        name = entry.get("name")
        if not is_nonblank_string(tag):
            add_issue(issues, ERROR, file, f"{path}.tag", "tag is required")
        elif tag not in DESIGN_TAGS:
            add_issue(issues, ERROR, file, f"{path}.tag", f"unsupported design tag: {tag}")

        if not is_nonblank_string(name):
            add_issue(issues, ERROR, file, f"{path}.name", "name is required")
            name = "<unnamed>"

        if tag != "domain_event" and not is_nonblank_string(entry.get("package")):
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.package",
                "package is required except for domain_event",
            )

        entry_fields = set(entry)
        for field_name in sorted(DESIGN_REMOVED_FIELDS & entry_fields):
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.{field_name}",
                f"removed design entry field: {field_name}",
            )
        for field_name in sorted(entry_fields - DESIGN_PUBLIC_FIELDS - DESIGN_REMOVED_FIELDS):
            add_issue(issues, WARN, file, f"{path}.{field_name}", "unknown design entry field")

        result_fields = entry.get("resultFields")
        if result_fields is not None and tag not in RESULT_FIELD_TAGS:
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.resultFields",
                f"{tag} must not declare resultFields",
                "Transform drawing-board-like output before registering it as design JSON.",
            )
            add_issue(
                issues,
                RECOVERY_HINT,
                file,
                f"{path}.resultFields",
                "drawing-board-compatible JSON is not automatically valid design JSON",
                "Only register fragments that satisfy the design-json contract.",
            )

        if tag == "integration_event" and not is_nonblank_string(entry.get("eventName")):
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.eventName",
                f"integration_event {name} must declare eventName",
            )
        if "eventName" in entry and tag not in EVENT_NAME_TAGS:
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.eventName",
                "eventName is allowed only on domain_event and integration_event",
            )
        if "persist" in entry and tag != "domain_event":
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.persist",
                "persist is allowed only on domain_event",
            )

        validate_field_array(
            file,
            issues,
            str(name),
            entry.get("fields"),
            f"{path}.fields",
            domain_event=(tag == "domain_event"),
        )
        validate_field_array(
            file,
            issues,
            str(name),
            result_fields,
            f"{path}.resultFields",
            domain_event=False,
        )


def optional_string_list(
    value: Any,
    file: Path,
    issues: list[Issue],
    path: str,
) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list):
        add_issue(issues, ERROR, file, path, "must be an array of strings")
        return []

    result: list[str] = []
    for index, item in enumerate(value):
        if not is_nonblank_string(item):
            add_issue(issues, ERROR, file, f"{path}[{index}]", "must be a nonblank string")
        else:
            result.append(item)
    return result


def validate_enum_manifest(file: Path, issues: list[Issue]) -> None:
    data = read_json_file(file, issues)
    if data is None:
        return
    if not isinstance(data, list):
        add_issue(issues, ERROR, file, "$", "enum manifest root must be an array")
        return

    shared_names: set[str] = set()
    owned_names: set[tuple[str, str]] = set()
    for index, entry in enumerate(data):
        path = f"$[{index}]"
        if not isinstance(entry, dict):
            add_issue(issues, ERROR, file, path, "enum manifest entry must be an object")
            continue

        name = entry.get("name")
        if not is_nonblank_string(name):
            add_issue(issues, ERROR, file, f"{path}.name", "name is required")
            name = "<unnamed>"
        if not is_nonblank_string(entry.get("package")):
            add_issue(issues, ERROR, file, f"{path}.package", "package is required")
        if "generateTranslation" in entry:
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.generateTranslation",
                "generateTranslation is removed from enum manifests",
            )

        aggregates = optional_string_list(entry.get("aggregates"), file, issues, f"{path}.aggregates")
        if len(aggregates) > 1:
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.aggregates",
                f"enum {name} may declare at most one aggregate",
            )

        if aggregates:
            key = (aggregates[0], str(name))
            if key in owned_names:
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{path}.name",
                    f"duplicate aggregate enum definition: {name} in {aggregates[0]}",
                )
            owned_names.add(key)
        else:
            enum_name = str(name)
            if enum_name in shared_names:
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{path}.name",
                    f"duplicate shared enum definition: {name}",
                )
            shared_names.add(enum_name)

        items = entry.get("items")
        if not isinstance(items, list):
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.items",
                "items is required and must be an array",
            )
            continue
        for item_index, item in enumerate(items):
            item_path = f"{path}.items[{item_index}]"
            if not isinstance(item, dict):
                add_issue(issues, ERROR, file, item_path, "enum item must be an object")
                continue
            value = item.get("value")
            if not isinstance(value, int) or isinstance(value, bool):
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{item_path}.value",
                    "value is required and must be an integer",
                )
            if not isinstance(item.get("name"), str):
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{item_path}.name",
                    "name is required and must be a string",
                )
            if not isinstance(item.get("desc"), str):
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{item_path}.desc",
                    "desc is required and must be a string",
                )


def validate_value_object_manifest(file: Path, issues: list[Issue]) -> None:
    data = read_json_file(file, issues)
    if data is None:
        return
    if not isinstance(data, list):
        add_issue(issues, ERROR, file, "$", "value-object manifest root must be an array")
        return

    shared_names: set[str] = set()
    owned_names: set[tuple[str, str]] = set()
    for index, entry in enumerate(data):
        path = f"$[{index}]"
        if not isinstance(entry, dict):
            add_issue(issues, ERROR, file, path, "value-object manifest entry must be an object")
            continue

        name = entry.get("name")
        if not is_nonblank_string(name):
            add_issue(issues, ERROR, file, f"{path}.name", "name is required")
            name = "<unnamed>"
        if not is_nonblank_string(entry.get("package")):
            add_issue(issues, ERROR, file, f"{path}.package", "package is required")

        for removed in ("scope", "aggregate"):
            if removed in entry:
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{path}.{removed}",
                    f"{removed} is removed; use aggregates",
                )

        storage = entry.get("storage", "json")
        if storage != "json":
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.storage",
                f"value object {name} storage must be json",
            )

        aggregates = optional_string_list(entry.get("aggregates"), file, issues, f"{path}.aggregates")
        if len(aggregates) > 1:
            add_issue(
                issues,
                ERROR,
                file,
                f"{path}.aggregates",
                f"value object {name} may declare at most one aggregate",
            )

        if aggregates:
            key = (aggregates[0], str(name))
            if key in owned_names:
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{path}.name",
                    f"duplicate aggregate value object definition: {name} in {aggregates[0]}",
                )
            owned_names.add(key)
        else:
            value_object_name = str(name)
            if value_object_name in shared_names:
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{path}.name",
                    f"duplicate shared value object definition: {name}",
                )
            shared_names.add(value_object_name)

        fields = entry.get("fields", [])
        if not isinstance(fields, list):
            add_issue(issues, ERROR, file, f"{path}.fields", "fields must be an array")
            continue
        for field_index, field in enumerate(fields):
            field_path = f"{path}.fields[{field_index}]"
            if not isinstance(field, dict):
                add_issue(issues, ERROR, file, field_path, "field item must be an object")
                continue
            if not is_nonblank_string(field.get("name")):
                add_issue(issues, ERROR, file, f"{field_path}.name", "name is required")
            if not is_nonblank_string(field.get("type")):
                add_issue(issues, ERROR, file, f"{field_path}.type", "type is required")
            if "nullable" in field and not isinstance(field.get("nullable"), bool):
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{field_path}.nullable",
                    "nullable must be a boolean",
                )
            if "defaultValue" in field and not isinstance(field.get("defaultValue"), str):
                add_issue(
                    issues,
                    ERROR,
                    file,
                    f"{field_path}.defaultValue",
                    "defaultValue must be a string",
                )


def unescape_sql_comment(value: str) -> str:
    return value.replace("''", "'")


def parse_annotations(comment: str) -> list[tuple[str, str, bool]]:
    return [
        (match.group(1), match.group(3) or "", match.group(2) is not None)
        for match in ANNOTATION_RE.finditer(comment)
    ]


def has_any(names: set[str], candidates: set[str]) -> bool:
    return bool(names & candidates)


def validate_annotation_values(
    file: Path,
    issues: list[Issue],
    path: str,
    annotations: list[tuple[str, str, bool]],
    context: str,
) -> None:
    names = {name for name, _, _ in annotations}
    for name, value, has_equals in annotations:
        if name in PRESENCE_ANNOTATIONS and has_equals:
            add_issue(
                issues,
                ERROR,
                file,
                path,
                f"@{name} does not accept an explicit value",
            )
        if name in BOOLEAN_ANNOTATIONS and (
            not has_equals or not value or value not in ("true", "false")
        ):
            add_issue(
                issues,
                ERROR,
                file,
                path,
                f"@{name} must declare lowercase true or false",
            )
        if name in REQUIRED_VALUE_ANNOTATIONS and (
            not has_equals or not value.strip()
        ):
            add_issue(issues, ERROR, file, path, f"@{name} requires a nonblank value")
        if name == "GeneratedValue" and value not in ("identity", "database-identity"):
            add_issue(
                issues,
                ERROR,
                file,
                path,
                f"unsupported @GeneratedValue strategy: {value}",
            )
        if name in {"Relation", "Rel"} and has_equals and value:
            if value.upper() not in RELATION_TYPES:
                add_issue(issues, ERROR, file, path, f"unsupported @{name} value: {value}")

    if context == "table":
        has_parent = has_any(names, {"Parent", "P"})
        has_root_true = any(
            value == "true"
            for name, value, _ in annotations
            if name in {"AggregateRoot", "Root", "R"}
        )
        if has_parent and has_root_true:
            add_issue(
                issues,
                ERROR,
                file,
                path,
                "@Parent/@P cannot be combined with aggregate-root true",
            )
    if context == "column":
        if has_any(names, {"E", "ENUM"}) and not has_any(names, {"T", "TYPE"}):
            add_issue(issues, ERROR, file, path, "@E/@ENUM requires @T/@TYPE")
        if has_any(names, {"Relation", "Rel", "Lazy", "L", "Count", "C"}):
            if not has_any(names, {"Reference", "Ref"}):
                add_issue(
                    issues,
                    ERROR,
                    file,
                    path,
                    "@Relation/@Rel, @Lazy/@L, and @Count/@C require @Reference/@Ref",
                )
        if "RefAggregate" in names and has_any(names, {"Reference", "Ref"}):
            add_issue(
                issues,
                ERROR,
                file,
                path,
                "@RefAggregate conflicts with @Reference/@Ref",
            )
        if "RefAggregate" in names and "RefId" in names:
            add_issue(issues, ERROR, file, path, "@RefAggregate conflicts with @RefId")


def validate_comment(
    file: Path,
    issues: list[Issue],
    path: str,
    comment: str,
    context: str,
) -> None:
    annotations = parse_annotations(comment)
    if not annotations:
        return

    if context == "table":
        allowed = TABLE_ANNOTATIONS
        rejected = REJECTED_TABLE_ANNOTATIONS
        wrong_context = COLUMN_ANNOTATIONS | REJECTED_COLUMN_ANNOTATIONS
    elif context == "column":
        allowed = COLUMN_ANNOTATIONS
        rejected = REJECTED_COLUMN_ANNOTATIONS
        wrong_context = TABLE_ANNOTATIONS | REJECTED_TABLE_ANNOTATIONS
    else:
        allowed = TABLE_ANNOTATIONS | COLUMN_ANNOTATIONS
        rejected = set()
        wrong_context = set()

    for name, _, _ in annotations:
        if name in rejected:
            add_issue(issues, ERROR, file, path, f"unsupported {context} annotation @{name}")
        elif name in wrong_context:
            add_issue(
                issues,
                ERROR,
                file,
                path,
                f"@{name} is not valid in {context} comments",
            )
        elif context == "unknown" and name in (
            REJECTED_TABLE_ANNOTATIONS | REJECTED_COLUMN_ANNOTATIONS
        ):
            add_issue(
                issues,
                WARN,
                file,
                path,
                f"@{name} is rejected in known table or column contexts",
            )
        elif name not in allowed:
            add_issue(issues, WARN, file, path, f"unknown {context} annotation @{name}")

    if context in {"table", "column"}:
        validate_annotation_values(file, issues, path, annotations, context)
    else:
        add_issue(
            issues,
            WARN,
            file,
            path,
            "annotation context is unknown",
            "Use explicit table or column comments, or make inline placement unambiguous.",
        )


def find_spans(pattern: re.Pattern[str], text: str) -> list[tuple[int, int]]:
    return [match.span() for match in pattern.finditer(text)]


def is_inside_span(start: int, end: int, spans: list[tuple[int, int]]) -> bool:
    return any(span_start <= start and end <= span_end for span_start, span_end in spans)


def line_bounds(text: str, index: int) -> tuple[int, int]:
    line_start = text.rfind("\n", 0, index) + 1
    line_end = text.find("\n", index)
    if line_end == -1:
        line_end = len(text)
    return line_start, line_end


def infer_mysql_comment_context(text: str, comment_start: int) -> str:
    prefix = text[:comment_start]
    create_index = prefix.lower().rfind("create table")
    if create_index == -1:
        return "unknown"

    create_body = prefix[create_index:]
    depth = 0
    saw_open = False
    for char in create_body:
        if char == "(":
            saw_open = True
            depth += 1
        elif char == ")" and depth > 0:
            depth -= 1
    if saw_open and depth == 0:
        suffix_after_close = create_body[create_body.rfind(")") + 1 :]
        if not re.search(r"[,;]", suffix_after_close):
            return "table"

    line_start, _ = line_bounds(text, comment_start)
    line_prefix = text[line_start:comment_start].strip()
    if saw_open and depth > 0 and line_prefix and not line_prefix.startswith(")"):
        return "column"
    return "unknown"


def validate_schema(file: Path, issues: list[Issue]) -> None:
    try:
        text = file.read_text(encoding="utf-8")
    except FileNotFoundError:
        add_issue(issues, ERROR, file, "$", "file does not exist")
        return

    explicit_spans = (
        find_spans(COMMENT_ON_TABLE_RE, text) + find_spans(COMMENT_ON_COLUMN_RE, text)
    )

    for index, match in enumerate(COMMENT_ON_TABLE_RE.finditer(text)):
        validate_comment(
            file,
            issues,
            f"COMMENT_ON_TABLE[{index}]",
            unescape_sql_comment(match.group(1)),
            "table",
        )
    for index, match in enumerate(COMMENT_ON_COLUMN_RE.finditer(text)):
        validate_comment(
            file,
            issues,
            f"COMMENT_ON_COLUMN[{index}]",
            unescape_sql_comment(match.group(1)),
            "column",
        )
    for index, match in enumerate(MYSQL_COMMENT_RE.finditer(text)):
        if is_inside_span(match.start(), match.end(), explicit_spans):
            continue
        validate_comment(
            file,
            issues,
            f"COMMENT[{index}]",
            unescape_sql_comment(match.group(1)),
            infer_mysql_comment_context(text, match.start()),
        )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate cap4k generator input files."
    )
    parser.add_argument(
        "--design", action="append", default=[], help="Design JSON file. Repeatable."
    )
    parser.add_argument(
        "--schema", action="append", default=[], help="DDL SQL file. Repeatable."
    )
    parser.add_argument(
        "--enum",
        action="append",
        default=[],
        help="Enum manifest JSON file. Repeatable.",
    )
    parser.add_argument(
        "--value-object",
        action="append",
        default=[],
        help="Value-object manifest JSON file. Repeatable.",
    )
    parser.add_argument("--json", action="store_true", help="Emit structured JSON output.")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    issues: list[Issue] = []

    for value in args.design:
        validate_design(Path(value), issues)
    for value in args.schema:
        validate_schema(Path(value), issues)
    for value in args.enum:
        validate_enum_manifest(Path(value), issues)
    for value in args.value_object:
        validate_value_object_manifest(Path(value), issues)

    emit_output(issues, args.json)
    return 1 if any(issue.level == ERROR for issue in issues) else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
