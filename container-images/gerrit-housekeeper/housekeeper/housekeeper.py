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

from projects.gc import GitGarbageCollection

logging.basicConfig(
    level=logging.DEBUG,
    stream=sys.stdout,
    format="%(asctime)s [%(levelname)s] %(message)s"
)

@click.group()
def cli():
    pass

@click.group(name="projects")
def cli_projects():
    """Commands for maintaining git repositories"""
    pass

@cli_projects.command()
@click.option("-s", "--skip", multiple=True)
@click.option("-p", "--project", multiple=True)
@click.option("--bitmap/--no-bitmap", " /-B", default=True)
@click.option("--pack-refs/--no-pack-refs", " /-R", default=True)
@click.option("--preserve-packs/--no-preserve-packs", "-P/ ", default=False)
def gc(skip, project, bitmap, pack_refs, preserve_packs):
    """ Run Git GC on repositories

        By default the script will run git-gc for all projects unless
        -p option is provided

        \b
        Examples:
            Run git-gc for all projects but skip foo and bar/baz projects
            gc -s foo -s bar/baz
            Run git-gc only for foo and bar/baz projects
            gc -p foo -p bar/baz
            Run git-gc only for bar project without writing bitmaps
            gc -p bar -b bar

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
        an empty file named "gc-aggresssive" in the same folder:

            \b
            $ cd $SITE/git/X.git
            $ touch gc-aggressive

        Every next git gc on this repository will use --aggressive option.
    """
    GitGarbageCollection(bitmap, pack_refs, preserve_packs).gc(project, skip)

cli.add_command(cli_projects)

if __name__ == "__main__":
    cli()
