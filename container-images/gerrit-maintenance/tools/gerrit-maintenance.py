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

import click
import logging
import sys

from projects.gc import GitGarbageCollectionProvider

logging.basicConfig(
    level=logging.DEBUG,
    stream=sys.stdout,
    format="%(asctime)s [%(levelname)s] %(message)s",
)


@click.group()
@click.option("-d", "--site", default="/var/gerrit", help="Path to Gerrit site.")
@click.pass_context
def cli(ctx, site):
    ctx.ensure_object(dict)
    ctx.obj["SITE"] = site


@click.group(name="projects")
@click.pass_context
def cli_projects(ctx):
    """Commands for maintaining git repositories"""
    pass


@cli_projects.command()
@click.pass_context
@click.option(
    "-s",
    "--skip",
    multiple=True,
    help="Which project to skip. Can be used multiple times.",
)
@click.option(
    "-p",
    "--project",
    multiple=True,
    help=(
        "Which project to gc. Can be used multiple times. If not given, all "
        "attrs=projects (except for `--skipped` ones) will be gc'ed."
    ),
)
@click.option(
    "-b/-B", "--bitmap/--no-bitmap", default=True, help="Whether to create bitmaps"
)
@click.option(
    "-r/-R", "--pack-refs/--no-pack-refs", default=True, help="Whether to pack refs"
)
@click.option(
    "-k/-K",
    "--preserve-packs/--no-preserve-packs",
    default=False,
    help="Whether to preserve packs",
)
def gc(ctx, skip, project, bitmap, pack_refs, preserve_packs):
    """Run Git GC on repositories

    By default the script will run git-gc for all projects unless
    -p option is provided

    \b
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

        \b
        $ cd $SITE/git/X.git
        $ touch gc-aggressive-once

    On the next run, gc.sh will use --aggressive option for gc-ing this
    repository *and* will remove this file. Next time, gc.sh again runs
    normal gc for this repository.

    To specify a permanent --aggressive git gc for a repository, create
    an empty file named "gc-aggressive" in the same folder:

        \b
        $ cd $SITE/git/X.git
        $ touch gc-aggressive

    Every next git gc on this repository will use --aggressive option.
    """
    GitGarbageCollectionProvider.get(
        ctx.obj["SITE"], bitmap, pack_refs, preserve_packs
    ).gc(project, skip)


cli.add_command(cli_projects)

if __name__ == "__main__":
    cli()
