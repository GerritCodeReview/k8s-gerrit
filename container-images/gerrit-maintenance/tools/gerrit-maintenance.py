#!/usr/bin/python3

# Copyright (C) 2024 The Android Open Source Project
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
import logging
import sys

from projects.gc import GitGarbageCollectionProvider

logging.basicConfig(
    level=logging.DEBUG,
    stream=sys.stdout,
    format="%(asctime)s [%(levelname)s] %(message)s",
)

def _run_projects_gc(args):
    GitGarbageCollectionProvider.get(
        args.site, args.create_bitmap, args.pack_refs, args.preserve_packs
    ).gc(args.projects, args.skip_projects)


def cli():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-d",
        "--site",
        help="Path to Gerrit site",
        dest="site",
        action="store",
        default="/var/gerrit",
    )

    subparsers = parser.add_subparsers()

    parser_projects = subparsers.add_parser(
        "projects", help="Tools for working with Gerrit projects."
    )
    parser_projects.add_argument(
        "-p",
        "--project",
        help=(
            "Which project to gc. Can be used multiple times. If not given, all "
            "attrs=projects (except for `--skipped` ones) will be gc'ed."
        ),
        dest="projects",
        action="append",
        default=[],
    )
    parser_projects.add_argument(
        "-s",
        "--skip",
        help="Which project to skip. Can be used multiple times.",
        dest="skip_projects",
        action="append",
        default=[],
    )
    parser_projects.set_defaults(func=lambda x: parser_projects.print_usage())

    subparsers_projects = parser_projects.add_subparsers()
    parser_projects_gc = subparsers_projects.add_parser(
        "gc", help="Execute Git Garbage Collection.", description="""
            Run Git GC on repositories

            By default the script will run git-gc for all projects unless
            -p option is provided

            Examples:
                Run git-gc for all projects but skip foo and bar/baz projects
                gc -s foo -s bar/baz
                Run git-gc only for foo and bar/baz projects
                gc -p foo -p bar/baz
                Run git-gc only for bar project without writing bitmaps
                gc -p bar -B

            To specify a one-time --aggressive git gc for a repository X, simply
            create an empty file called 'gc-aggressive-once' in the $SITE/git/X.git
            folder:

                $ cd $SITE/git/X.git
                $ touch gc-aggressive-once

            On the next run, gc.sh will use --aggressive option for gc-ing this
            repository *and* will remove this file. Next time, gc.sh again runs
            normal gc for this repository.

            To specify a permanent --aggressive git gc for a repository, create
            an empty file named "gc-aggressive" in the same folder:

                $ cd $SITE/git/X.git
                $ touch gc-aggressive

            Every next git gc on this repository will use --aggressive option.
            """, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser_projects_gc.add_argument(
        "-B",
        "--no-bitmap",
        help="Whether to create bitmaps",
        dest="create_bitmap",
        action="store_false",
    )
    parser_projects_gc.add_argument(
        "-R",
        "--no-pack-refs",
        help="Whether to pack refs",
        dest="pack_refs",
        action="store_false",
    )
    parser_projects_gc.add_argument(
        "-k",
        "--preserve-packs",
        help="Whether to preserve packs",
        dest="preserve_packs",
        action="store_true",
    )
    parser_projects_gc.set_defaults(func=_run_projects_gc)

    args = parser.parse_args()
    args.func(args)

if __name__ == "__main__":
    cli()
