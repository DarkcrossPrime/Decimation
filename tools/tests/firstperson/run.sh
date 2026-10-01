#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
source_dir="$repo_root/src/client/java/com/decimation/client/firstperson"
compiler=(javac)
if ! command -v javac >/dev/null 2>&1; then
    compiler=(java com.sun.tools.javac.Main)
fi
"${compiler[@]}" --release 17 -d "$test_dir" \
    "$source_dir/FirstPersonRigPose.java" \
    "$source_dir/FirstPersonRigSolver.java" \
    "$source_dir/FirstPersonSupportArmSolver.java" \
    "$repo_root/src/client/java/com/decimation/client/content/ObjModel.java" \
    "$repo_root/tools/tests/firstperson/FirstPersonRigSolverTest.java" \
    "$repo_root/tools/tests/firstperson/FirstPersonSupportArmSolverTest.java"
java -cp "$test_dir" com.decimation.client.firstperson.FirstPersonRigSolverTest
java -cp "$test_dir" com.decimation.client.firstperson.FirstPersonSupportArmSolverTest
