#!/usr/bin/env bash
set +x
set -euo pipefail

overwrite=false
positional_arguments=()
for argument in "$@"; do
  case "$argument" in
    --overwrite) overwrite=true ;;
    -*) echo "Unknown option: $argument" >&2; exit 1 ;;
    *) positional_arguments+=("$argument") ;;
  esac
done
if [[ ${#positional_arguments[@]} -gt 2 ]]; then
  echo "Usage: bash $0 [--overwrite] [owner/repo] [keystore file]" >&2
  exit 1
fi
set -- "${positional_arguments[@]}"

for required_command in git gh keytool base64 tr; do
  command -v "$required_command" >/dev/null || { echo "Required command not found: $required_command" >&2; exit 1; }
done

repository="${1:-$(gh repo view "$(git remote get-url origin)" --json nameWithOwner --jq .nameWithOwner)}"
if [[ ! "$repository" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]; then
  echo "Repository must be in owner/repo format." >&2
  exit 1
fi
key_alias="$(printf '%s' "${repository##*/}" | tr '[:upper:]' '[:lower:]')"
keystore_file="${2:-../$key_alias-release.keystore}"

gh api -X PUT "repos/$repository/environments/release" --silent --input - <<< '{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}'
policy_count="$(gh api "repos/$repository/environments/release/deployment-branch-policies" --jq '[.branch_policies[] | select(.name == "v*" and .type == "tag")] | length')"
if [[ "$policy_count" == 0 ]]; then
  gh api -X POST "repos/$repository/environments/release/deployment-branch-policies" --silent -f name='v*' -f type=tag
fi

if [[ -e "$keystore_file" && ! -f "$keystore_file" ]]; then
  echo "Keystore is not a regular file: $keystore_file" >&2
  exit 1
fi
if [[ "$overwrite" == true ]]; then
  echo "WARNING: A new signing key will replace the local keystore and CI secrets. Existing installations signed with the old key cannot update to this key. Back up the original keystore first." >&2
elif [[ ! -e "$keystore_file" ]]; then
  existing_keystore="$(gh secret list --env release --repo "$repository" --json name --jq '[.[] | select(.name == "KEYSTORE_BASE64")] | length')"
  if [[ "$existing_keystore" != 0 ]]; then
    echo "CI already has a signing keystore. Restore the original local file; refusing to generate a replacement." >&2
    exit 1
  fi
elif [[ ! -s "$keystore_file" ]]; then
  echo "Keystore is not a nonempty regular file: $keystore_file" >&2
  exit 1
fi

printf 'Repository: %s\nKey alias: %s\nKeystore: %s\n' "$repository" "$key_alias" "$keystore_file"
read -r -s -p 'Keystore/key password (at least 6 characters): ' RELEASE_SIGNING_PASSWORD
printf '\n' >&2
if [[ ${#RELEASE_SIGNING_PASSWORD} -lt 6 ]]; then
  echo "Password must contain at least 6 characters." >&2
  exit 1
fi
export RELEASE_SIGNING_PASSWORD
keystore_work=""
trap 'unset RELEASE_SIGNING_PASSWORD; if [[ -n "$keystore_work" ]]; then rm -rf "$keystore_work"; fi' EXIT

if [[ "$overwrite" == true || ! -e "$keystore_file" ]]; then
  keystore_work="$(mktemp -d "$keystore_file.tmp.XXXXXX")"
  keytool -genkeypair -v -keystore "$keystore_work/release.keystore" -storetype PKCS12 \
    -alias "$key_alias" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=$key_alias" -storepass:env RELEASE_SIGNING_PASSWORD -keypass:env RELEASE_SIGNING_PASSWORD
  mv -f -- "$keystore_work/release.keystore" "$keystore_file"
else
  keytool -certreq -keystore "$keystore_file" -alias "$key_alias" \
    -storepass:env RELEASE_SIGNING_PASSWORD -keypass:env RELEASE_SIGNING_PASSWORD >/dev/null
fi

base64 < "$keystore_file" | tr -d '\r\n' | gh secret set KEYSTORE_BASE64 --env release --repo "$repository"
printf '%s' "$key_alias" | gh secret set KEY_ALIAS --env release --repo "$repository"
for secret_name in KEYSTORE_PASSWORD KEY_PASSWORD; do
  printf '%s' "$RELEASE_SIGNING_PASSWORD" | gh secret set "$secret_name" --env release --repo "$repository"
done
printf 'Signing secrets configured. Keep the keystore and password securely backed up.\n'