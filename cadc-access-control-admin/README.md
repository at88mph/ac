# cadcAccessControl-Admin

This module provides a command line tool for managing users. It uses the persistence layer code (rather than the web
service) for the various functions.

## configuration

See the [cadc-java](https://github.com/opencadc/docker-base/tree/master/cadc-java)
image docs for general config requirements.

Runtime configuration must be made available via the `/config` directory.

TODO: document config files

## building it
```
gradle clean build
docker build -t cadc-access-control-admin -f Dockerfile .
```

## checking it
```
docker run -it cadc-access-control-admin:latest /bin/bash
```

## running it to display command-line help
```
docker run --rm --user opencadc:opencadc -v /path/to/external/config:/config:ro \
    cadc-access-control-admin:latest /cadc-access-control-admin/bin/cadc-access-control-admin --help
```
Important: in the above usage, the args **replace** the CMD from the Dockerfile so you have to include it here.
This could be improved, but the ENTRYPOINT is provided by the base image and does some setup before executing
the CMD.... TBD.
