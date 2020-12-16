![CI/CD](https://github.com/sogis/ili2gpkg-web-service-ws/workflows/CI/CD/badge.svg)

# ili2gpkg-web-service-ws

The ili2gpkg web service is a _spring boot_ application and uses _ili2gpkg_ for the import of an INTERLIS transfer file into a geopackage file.

## TODO
- Tests with different browsers
- ...

## Features

* imports an interlis transfer file into a geopackage file
* no ili2gpkg options are exposed 

## License

ili2gpkg web service is licensed under the [MIT License](LICENSE).

## Status

ili2gpkg web service is in development state.

## System Requirements

For the current version of ili2gpkg web service, you will need a JRE (Java Runtime Environment) installed on your system, version 1.8 or later.

## Developing

ili2gpkg web service is build as a Spring Boot Application.

`git clone https://github.com/edigonzales/ili2gpkg-web-service-ws.git` 

Use your favorite IDE (e.g. [Spring Tool Suite](https://spring.io/tools/sts/all)) for coding.

### Update QGIS project (for Gefahrenkartierung)
If AFU wants to change the QGIS project file and sends a new one: Rename it to `*.zip`, then unzip it and search and replace `<datasource>./so_wie_es_daher_kommt.gpkg` with `<datasource>./GKSO11.gpkg` and `source="./so_wie_es_daher_kommt.gpkg` with `source="./GKSO11.gpkg`. 

### Testing

Since _ili2gpkg_ is heavily tested in its own project, there are only functional tests of the web service implemented.

`./gradlew clean test` will run all tests by starting the web service and uploading an INTERLIS transfer file.

### Building

`./gradlew clean build` will create an executable JAR. Ilivalidator custom functions will not work. Not sure why but must be something with how the plugin loader works. 

### Release management / versioning

It uses a simple release management and versioning mechanism: Local builds are tagged as `1.0.LOCALBUILD`. Builds on Travis, Jenkins or Github Action will append the build number, e.g. `1.0.48`. Major version will be increased after "major" changes. After every commit to the repository a docker image will be build and pushed to `hub.docker.com`. It will be tagged as `latest` and with the build number (`1.0.48`).

## Running as Docker Image 

### Local
```
docker run -p 8080:8080 sogis/ili2gpkg-web-service:latest
```

