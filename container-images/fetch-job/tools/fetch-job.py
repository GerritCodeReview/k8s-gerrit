# Copyright (C) 2026 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import argparse
import subprocess
import sys

import yaml

GIT_HOME = "/var/gerrit/git"
DEFAULT_CONFIG_FILE = "/var/gerrit/etc/incoming-replication.config.yaml"
DEFAULT_REFSPEC_TEMPLATE = "+refs/heads/*:refs/heads/{remote_name}/*"


def parse_timeout(value):
    """Convert a timeout string like '5m', '30s', or '120' to seconds (int)."""
    if value is None or value == "null":
        return None
    value = str(value).strip()
    if value.endswith("m"):
        return int(value[:-1]) * 60
    if value.endswith("s"):
        return int(value[:-1])
    return int(value)


def git(git_dir, *args, timeout=None):
    cmd = ["git", f"--git-dir={git_dir}"] + list(args)
    subprocess.run(cmd, check=True, timeout=timeout)


def configure_remote(git_dir, remote_name, remote_url, refspec):
    subprocess.run(
        [
            "git",
            f"--git-dir={git_dir}",
            "config",
            f"remote.{remote_name}.url",
            remote_url,
        ],
        check=True,
    )
    subprocess.run(
        [
            "git",
            f"--git-dir={git_dir}",
            "config",
            "--unset-all",
            f"remote.{remote_name}.fetch",
        ],
        # exit code 5 means the key did not exist — that is fine on first run
        check=False,
    )
    refspecs = refspec if isinstance(refspec, list) else [refspec]
    for spec in refspecs:
        subprocess.run(
            [
                "git",
                f"--git-dir={git_dir}",
                "config",
                "--add",
                f"remote.{remote_name}.fetch",
                spec,
            ],
            check=True,
        )


def process_remote(remote):
    remote_name = remote["name"]
    server_url = remote["url"].rstrip("/")
    timeout_seconds = parse_timeout(remote.get("timeout"))

    for fetch in remote.get("fetch", []):
        remote_repo = fetch["remoteRepo"]
        local_repo = fetch.get("localRepo") or remote_repo
        refspec = fetch.get("refSpec") or DEFAULT_REFSPEC_TEMPLATE.format(
            remote_name=remote_name
        )

        remote_url = f"{server_url}/{remote_repo}"
        git_dir = f"{GIT_HOME}/{local_repo}.git"

        configure_remote(git_dir, remote_name, remote_url, refspec)

        print(f"Fetching {remote_repo} from {remote_name}")
        try:
            subprocess.run(
                ["git", f"--git-dir={git_dir}", "fetch", remote_name],
                check=True,
                timeout=timeout_seconds,
            )
        except subprocess.TimeoutExpired:
            print(
                f"ERROR: fetch of {remote_repo} from {remote_name} timed out "
                f"after {timeout_seconds}s",
                file=sys.stderr,
            )
            sys.exit(1)


def main():
    parser = argparse.ArgumentParser(
        description="Fetch from remote Git repositories into local Gerrit repositories."
    )
    parser.add_argument(
        "--config",
        default=DEFAULT_CONFIG_FILE,
        metavar="CONFIG_FILE",
        help=f"Path to the YAML config file (default: {DEFAULT_CONFIG_FILE})",
    )
    args = parser.parse_args()

    try:
        with open(args.config) as f:
            config = yaml.safe_load(f)
    except FileNotFoundError:
        print(f"ERROR: config file not found: {args.config}", file=sys.stderr)
        sys.exit(1)

    for remote in config.get("remotes", []):
        process_remote(remote)


if __name__ == "__main__":
    main()
