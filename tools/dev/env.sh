#!/usr/bin/env bash
# Source this file from bash or zsh; it does not modify shell startup files.
if [[ -n "${ZSH_VERSION:-}" ]]; then
    decimation_env_source="${(%):-%x}"
else
    decimation_env_source="${BASH_SOURCE[0]}"
fi
decimation_env_root="$(cd -- "$(dirname -- "$decimation_env_source")/../.." && pwd)"
if [[ -x "$decimation_env_root/.toolchains/jdk-25/bin/java" ]]; then
    export JAVA_HOME="$decimation_env_root/.toolchains/jdk-25"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
unset decimation_env_root decimation_env_source
