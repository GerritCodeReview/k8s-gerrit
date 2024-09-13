# Gerrit-Maintenance

The scripts provided here provide a CLI to run maintenance tasks for a Gerrit
installation.

To run the CLI use:

```sh
python ./gerrit-maintenance.py
```

Supported tasks are:

- Projects
  - Garbage Collection

## Projects

Tasks related to Gerrit projects/repositories.

```sh
python ./gerrit-maintenance.py projects
```

Options:

- `-s` \ `--skip`: Which project to skip. Can be used multiple times.
- `-p` \ `--project`: Which project to gc. Can be used multiple times. If not \
    given, all projects (except for `--skipped` ones) in the Gerrit site will
    be used.

### Garbage Collection

Run Git garbage collection in the repositories served by Gerrit.

```sh
python ./gerrit-maintenance.py projects gc
```

Options:

- `-b\-B` \ `--bitmap/--no-bitmap`: Whether to create bitmaps (default: true)
- `-r\-R` \ `--pack-refs/--no-pack-refs`: Whether to pack refs (default: true)
- `-k\-K` \ `--preserve-packs/--no-preserve-packs`: Whether to preserve packs \
    (default: false)
