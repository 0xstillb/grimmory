#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: tools/fork-diff-guard.sh [--strict] [baseline-ref]

Compares HEAD against the merge-base with baseline-ref and reports whether the
fork diff stays inside documented ownership areas.

Options:
  --strict       Exit non-zero when suspicious files are found.
  -h, --help     Show this help.

Environment:
  DIFF_GUARD_STRICT=1   Same as --strict.

Default baseline-ref: upstream/develop
EOF
}

strict="${DIFF_GUARD_STRICT:-0}"
baseline="upstream/develop"
safe_directory="$(pwd -W 2>/dev/null || pwd)"

git_cmd() {
  git -c "safe.directory=$safe_directory" "$@"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --strict)
      strict="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      baseline="$1"
      shift
      ;;
  esac
done

repo_root="$(git_cmd rev-parse --show-toplevel)"
cd "$repo_root"
safe_directory="$(pwd -W 2>/dev/null || pwd)"

if ! git_cmd rev-parse --verify --quiet "$baseline" >/dev/null; then
  echo "Baseline ref not found: $baseline" >&2
  echo "Fetch or pass a valid baseline, for example: tools/fork-diff-guard.sh upstream/develop" >&2
  exit 2
fi

merge_base="$(git_cmd merge-base "$baseline" HEAD)"
mapfile -t changed_files < <(git_cmd diff --name-only "$merge_base"..HEAD | sort)

allowed_patterns=(
  "AGENTS.md"
  "Justfile"
  "backend/src/main/java/org/booklore/grimmlink/**"
  "backend/src/test/java/org/booklore/grimmlink/**"
  "backend/src/main/java/org/booklore/opf/**"
  "backend/src/test/java/org/booklore/opf/**"
  "backend/src/main/resources/db/migration/**"
  "docs/koreader-companion/**"
  "docs/GRIMMLINK-BACKEND-CHANGES.md"
  "docs/FORK-DIFF-GUARD.md"
  "tools/fork-diff-guard.sh"
)

documented_hook_files=(
  "backend/src/main/java/org/booklore/service/fileprocessor/AbstractFileProcessor.java"
  "backend/src/test/java/org/booklore/service/fileprocessor/AbstractFileProcessorTest.java"
  "backend/src/main/java/org/booklore/config/security/SecurityConfig.java"
  "backend/src/main/java/org/booklore/model/dto/progress/KoreaderProgress.java"
  "backend/src/main/java/org/booklore/model/entity/ReadingSessionEntity.java"
)

matches_pattern() {
  local file="$1"
  local pattern="$2"

  case "$pattern" in
    *"/**")
      local prefix="${pattern%/**}"
      [[ "$file" == "$prefix"/* ]]
      ;;
    *)
      [[ "$file" == "$pattern" ]]
      ;;
  esac
}

is_allowed() {
  local file="$1"
  local pattern

  for pattern in "${allowed_patterns[@]}" "${documented_hook_files[@]}"; do
    if matches_pattern "$file" "$pattern"; then
      return 0
    fi
  done

  return 1
}

is_core_file() {
  local file="$1"

  case "$file" in
    backend/src/main/java/org/booklore/grimmlink/*|backend/src/main/java/org/booklore/grimmlink/**)
      return 1
      ;;
    backend/src/main/java/org/booklore/opf/*|backend/src/main/java/org/booklore/opf/**)
      return 1
      ;;
    backend/src/test/java/org/booklore/grimmlink/*|backend/src/test/java/org/booklore/grimmlink/**)
      return 1
      ;;
    backend/src/test/java/org/booklore/opf/*|backend/src/test/java/org/booklore/opf/**)
      return 1
      ;;
    backend/src/main/java/org/booklore/*|backend/src/main/java/org/booklore/**)
      return 0
      ;;
    backend/src/test/java/org/booklore/*|backend/src/test/java/org/booklore/**)
      return 0
      ;;
    frontend/src/app/*|frontend/src/app/**)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

allowed_files=()
suspicious_files=()
core_files=()

for file in "${changed_files[@]}"; do
  file="${file%$'\r'}"

  if is_allowed "$file"; then
    allowed_files+=("$file")
  else
    suspicious_files+=("$file")
  fi

  if is_core_file "$file"; then
    core_files+=("$file")
  fi
done

print_list() {
  local title="$1"
  shift
  local items=("$@")

  echo
  echo "$title (${#items[@]})"
  if [[ ${#items[@]} -eq 0 ]]; then
    echo "  none"
    return
  fi

  local item
  for item in "${items[@]}"; do
    echo "  $item"
  done
}

echo "Fork diff guard"
echo "Baseline: $baseline"
echo "Merge-base: $merge_base"
echo "Mode: $([[ "$strict" == "1" ]] && echo "strict" || echo "report-only")"

print_list "Allowed changed files" "${allowed_files[@]}"
print_list "Suspicious changed files" "${suspicious_files[@]}"
print_list "Core files touched" "${core_files[@]}"

echo
echo "Suggested action"
if [[ ${#suspicious_files[@]} -eq 0 ]]; then
  echo "  Diff is inside the current allowlist. Keep reviewing documented hook files before merge."
else
  echo "  Review suspicious files. Move fork-only work into Grimmlink/OPF ownership areas or document a narrow hook before allowing it."
fi

if [[ "$strict" == "1" && ${#suspicious_files[@]} -gt 0 ]]; then
  exit 1
fi
