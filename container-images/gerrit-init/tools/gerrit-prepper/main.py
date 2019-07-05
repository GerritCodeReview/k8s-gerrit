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

from prepper.tasks import init, validate_db


def _run_init(args):
    init.GerritInit(args.site, args.wanted_plugins, args.reviewdb).execute()


def _run_validate_db(args):
    validate_db.execute(args.site)


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

    subparsers = parser.add_subparsers()

    parser_init = subparsers.add_parser("init", help="Initialize Gerrit site")
    parser_init.add_argument(
        "-p",
        "--plugin",
        help="Gerrit plugin to be installed. Can be used multiple times.",
        dest="wanted_plugins",
        action="append",
        default=list(),
    )
    parser_init.add_argument(
        "-d",
        "--reviewdb",
        help="Whether a reviewdb is part of the Gerrit installation.",
        dest="reviewdb",
        action="store_true",
    )
    parser_init.set_defaults(func=_run_init)

    parser_validate_db = subparsers.add_parser("validate-db", help="Validate ReviewDB")
    parser_validate_db.set_defaults(func=_run_validate_db)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
