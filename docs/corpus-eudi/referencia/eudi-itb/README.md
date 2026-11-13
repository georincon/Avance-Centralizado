# EUDI Interoperability Test Bed

**Important!** Before you proceed, please read
the [EUDI Wallet Reference Implementation project description](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Table of contents

* [Project overview](#project-overview)
* [Disclaimer](#disclaimer)
* [Quick reference](#quick-reference)
* [Repository structure](#repository-structure)
* [Using and uploading tests](#using-and-uploading-tests)
* [Run the ITB stack (core services)](#run-the-itb-stack-core-services)
* [About the pre-seeded data](#about-the-pre-seeded-data)
* [Build the custom handler (`verifier-testbed`)](#build-the-custom-handler-verifier-testbed)
* [Integrating the custom handler with the ITB stack](#integrating-the-custom-handler-with-the-itb-stack)
* [Tests](#tests)
* [How to contribute](#how-to-contribute)
* [License](#license)

## Project overview
This repository provides a local setup of the Interoperability Test Bed (ITB) together with custom handlers. It includes:
- The ITB Docker compose  
- A sample custom handler implementation (`verifier-testbed`) exposing GITB messaging and validation services  
- Example test artifacts and test suit you can zip and upload to the ITB UI  
- A pre-seeded ITB data import that is auto-restored at startup  

## Disclaimer

The released software is a initial development release version:
-  The initial development release is an early endeavor reflecting the efforts of a short timeboxed period, and by no means can be considered as the final product.
-  The initial development release may be changed substantially over time, might introduce new features but also may change or remove existing ones, potentially breaking compatibility with your existing code.
-  The initial development release is limited in functional scope.
-  The initial development release may contain errors or design flaws and other problems that could cause system or other failures and data loss.
-  The initial development release has reduced security, privacy, availability, and reliability standards relative to future releases. This could make the software slower, less reliable, or more vulnerable to attacks than mature software.
-  The initial development release is not yet comprehensively documented.
-  Users of the software must perform sufficient engineering and additional testing in order to properly evaluate their application and determine whether any of the open-sourced components is suitable for use in that application.
-  We strongly recommend to not put this version of the software into production use.
-  Only the latest version of the software will be supported

## Quick reference
- Start ITB: `cd ./docker && docker compose up -d`
- Get admin OTP: `docker logs -f itb-ui`

## Repository structure  
- `custom-handlers/verifier-testbed/`: Spring Boot implementation of GITB test services (messaging and validation).  
      Can be built and run as a Docker image. This handler is not included in the default Docker Compose.  
      If you want it running alongside ITB, you must add it manually (see below).  
- `example/`: Contains a zip of tests that can be uploaded to the ITB.
- `tests/`: Contains the raw test assets (e.g., `testSuite1.xml`, `tests/testCase1.xml`, `tests/testCase2.xml`).  
      Zip this folder to upload the same content to the Test Bed (the resulting zip should match  
      the one provided under `example/`).
- `docker/`: 
    - `docker-compose.yml`: Composition for ITB services (`gitb-redis`, `gitb-mysql`, `gitb-srv`, `gitb-ui`).
    - `data/export.zip`: Backup archive that the UI container restores on startup to pre-seed data.

## Using and uploading tests
- Test suit under `tests/`. Zip the content of this folder to produce an uploadable package (equivalent to the one found under `example/`).
- Upload the zip through the ITB UI more information can be found [here](https://www.itb.ec.europa.eu/docs/itb-ca/latest/domainDashboard/index.html#upload-test-suite).

## Run the ITB stack (core services)

### Start the stack:
```bash
cd ./docker
docker compose up -d
```

### Admin one-time password (OTP)
On the first startup, the administrator account uses a one-time password printed in the UI container logs:
```bash
docker logs -f itb-ui
```
Use this OTP to sign in at `http://localhost:9000` and then change the password.

## About the pre-seeded data
- The file `docker/data/export.zip` is a backup archive that is restored by the `gitb-ui` container on startup.
- The `gitb-ui` container uses the following notable environment variables in `docker-compose.yml`:
    - `DATA_ARCHIVE_KEY=passwd` (used to decrypt/restore the archive)
    - `AUTOMATION_API_ENABLED=true`

## Build the custom handler (`verifier-testbed`)

Create the necessary image for verifier-testbed information can be found in the [verifier-testbed README](custom-handlers/verifier-testbed/README.md)

## Integrating the custom handler with the ITB stack
The provided `docker-compose.yml` does not include the `verifier-testbed` custom handler. To run it alongside ITB:
1. Build the handler image (see [README here](custom-handlers/verifier-testbed/README.md)).
2. Extend `docker/docker-compose.yml` with a new service, for example:
    ```
      verifier-testbed:
        image: local/verifier-testbed
        container_name: verifier-testbed
        restart: unless-stopped
        ports:
          - "8080:8080"
        depends_on:
          gitb-srv:
            condition: service_started
    ```
3. Recreate the stack:
    ```bash
    docker compose up -d
    ```

## Tests

### Structure of the tests folder

Tests are organized as follows:
- `testSuite1.xml`: Contains definitions of the metadata, actors and includes the test cases to be loaded by ITB.
- `tests/`: Contains the test cases.
  - `tests/testCase1.xml`: A test case that requires the custom handler to be running.
  - `tests/testCase2.xml`: A test case that runs only with the basic XML test structure (no custom handlers).`

### How to add a new test case

1. Create a new XML file under `tests/` with a unique name and id.
2. Add the new file id reference to `testSuite1.xml` under the already existing test cases.
3. Zip the content of the `./test` folder to produce an uploadable package.
4. Upload the zip through the ITB UI.
5. Follow the instructions on the UI to update the already uploaded tests with the new ones.

### How to prepare the tests for the ITB

1. Create a zip file containing the `testSuite1.xml` and `tests/` folder.
2. Upload the zip file to the ITB UI.

## How to contribute

We welcome contributions to this project. To ensure that the process is smooth for everyone
involved, follow the guidelines found in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

### License details

Copyright (c) 2025-2026 European Commission

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

