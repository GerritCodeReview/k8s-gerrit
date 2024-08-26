# Gerrit-Indexer image

Container image for reindexing the indexes in a Gerrit site.

## Content

* the [gerrit-base](../gerrit-base/README.md) image
* `/var/tools/start`: start script

## Start

* starts the Gerrit `Reindex` pgm tool via start script `/var/tools/start`
* Using the `--output OUTPUT` option for the entrypoint script allows to copy
  the resulting indexes to an output directory. The output directory is mainly
  meant to be a mounted volume.
