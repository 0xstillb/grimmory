#!/usr/bin/env bash
set -euo pipefail

base_ref="${1:?Usage: $0 <base-ref> <head-ref>}"
head_ref="${2:?Usage: $0 <base-ref> <head-ref>}"
migration_dir="backend/src/main/resources/db/migration"

git rev-parse --verify "${base_ref}^{commit}" >/dev/null
git rev-parse --verify "${head_ref}^{commit}" >/dev/null

mapfile -t published_migrations < <(
  git ls-tree -r --name-only "${base_ref}" -- "${migration_dir}" \
    | grep -E "^${migration_dir}/V[0-9]+__.+\.sql$" || true
)

mutated_migrations=()
for migration in "${published_migrations[@]}"; do
  if ! git cat-file -e "${head_ref}:${migration}" 2>/dev/null; then
    mutated_migrations+=("${migration} (removed or renamed)")
  elif ! git diff --quiet "${base_ref}" "${head_ref}" -- "${migration}"; then
    mutated_migrations+=("${migration} (content changed)")
  fi
done

if ((${#mutated_migrations[@]} > 0)); then
  echo "Published Flyway migrations are immutable. Add a new migration instead of changing an existing one:" >&2
  printf '  - %s\n' "${mutated_migrations[@]}" >&2
  exit 1
fi

echo "Published Flyway migrations are unchanged between ${base_ref} and ${head_ref}."
