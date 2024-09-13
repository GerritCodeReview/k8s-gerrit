# Gerrit-Maintenance image

Container image containing a tool to perform maintenance tasks in Gerrit. It is
supposed to be used for scheduled and manual tasks.

## Content

* the [base](../base/README.md) image
* `/var/tools/gerrit-maintenance.py`: CLI script to run maintenance tasks

## Start

* Use `/var/tools/gerrit-maintenance.py` to start the CLI. Dependent on the
  subcommand different tasks may be performed.
