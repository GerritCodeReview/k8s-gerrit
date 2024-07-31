#!/usr/bin/python3

# Copyright (C) 2019 The Android Open Source Project
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

from initializer.config.cluster_mode import ClusterMode
from initializer.tasks import download_plugins, reindex, validate_notedb
from initializer.tasks.init_ha import GerritInitHA
from initializer.tasks.init_multisite import GerritInitMultisite
from initializer.config.init_config import InitConfig


def _run_download_plugins(args):
    config = InitConfig().parse(args.config)
    download_plugins.get_installer(args.site, config).execute()


def _run_init(args):
    config = InitConfig().parse(args.config)
    match config.cluster_mode:
        case ClusterMode.HIGH_AVAILABILITY:
            GerritInitHA(args.site, config).execute()
        case ClusterMode.MULTISITE:
            GerritInitMultisite(args.site, config).execute()
        case _:
            raise ValueError(f"Invalid clusterMode: {config.cluster_mode.name}")


def _run_reindex(args):
    config = InitConfig().parse(args.config)
    reindex.get_reindexer(args.site, config).start(args.force)


def _run_validate_notedb(args):
    validate_notedb.NoteDbValidator(args.site).wait_until_valid()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-s",
        "--site",
        help="Path to Gerrit site",
        dest="site",
        action="store",
        default="/var/gerrit",
        required=True,
    )
    parser.add_argument(
        "-c",
        "--config",
        help="Path to configuration file for init process.",
        dest="config",
        action="store",
        required=True,
    )

    subparsers = parser.add_subparsers()

    parser_download_plugins = subparsers.add_parser(
        "download-plugins", help="Download plugins"
    )
    parser_download_plugins.set_defaults(func=_run_download_plugins)

    parser_init = subparsers.add_parser("init", help="Initialize Gerrit site")
    parser_init.set_defaults(func=_run_init)

    parser_reindex = subparsers.add_parser("reindex", help="Reindex Gerrit indexes")
    parser_reindex.add_argument(
        "-f",
        "--force",
        help="Reindex even if indices are ready.",
        dest="force",
        action="store_true",
    )
    parser_reindex.set_defaults(func=_run_reindex)

    parser_validate_notedb = subparsers.add_parser(
        "validate-notedb", help="Validate NoteDB"
    )
    parser_validate_notedb.set_defaults(func=_run_validate_notedb)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
