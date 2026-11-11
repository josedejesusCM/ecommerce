#!/bin/bash
input=$(cat)
# Test classes may use field injection; production code may not.
if printf '%s' "$input" | grep -qE '"file_path"[^,}]*src[\\/]+test[\\/]'; then
  exit 0
fi
if printf '%s' "$input" | grep -q "@Autowired"; then
  echo "BLOCKED: @Autowired found. Use @RequiredArgsConstructor instead." >&2
  exit 2
fi
exit 0