# Relying Party registration service

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

:heavy_exclamation_mark: **Important!** Before you proceed, please read
the [EUDI Wallet Reference Implementation project description](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)


## Overview

As per the [European Digital Identity Wallet Architecture and Reference Framework Trust Model](https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/blob/main/docs/architecture-and-reference-framework-main.md),

+ Relying Parties are registered by a Relying Party Registrar in their Member State.
+ As a result of the registration, a Relying Party receives an access certificate (WRPAC) from a Relying Party Access CA and a registration certificate (WRPRC).

+ The RP access certificate is used by the Wallet Instance to authenticate the Relying Party Instance.
+ The RP registration certificate is used by the Wallet Instance to access the intended use and attribute access policies of a WRP.

+ Relying Party authentication is a process whereby a Relying Party proves its identity to a Wallet Instance, in the context of a transaction in which the Relying Party requests the Wallet Instance to release some attributes.
+ Relying Party authentication is included in the protocol used (both in ISO/IEC 18013-5 and OpenID4VP) by a Wallet Instance and a Relying Party Instance to communicate. 

For more detailed information:

+ [Registrars](https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/blob/main/docs/architecture-and-reference-framework-main.md#317-registrars)
+ [Access Certificate Authorities](https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/blob/main/docs/architecture-and-reference-framework-main.md#318-access-certificate-authorities)
+ [Providers of registration certificates](https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/blob/main/docs/architecture-and-reference-framework-main.md#319-providers-of-registration-certificates)
+ [CIR 2025/848](https://eur-lex.europa.eu/eli/reg_impl/2025/848/oj),
+ [TS5](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts5-common-formats-and-api-for-rp-registration-information.md)
+ [TS6](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts6-common-set-of-rp-information-to-be-registered.md)

The Relying Party Registration complies with the requirements set out by the [CIR 2025/848](https://eur-lex.europa.eu/eli/reg_impl/2025/848/oj), [TS5](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts5-common-formats-and-api-for-rp-registration-information.md) and [TS6](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts6-common-set-of-rp-information-to-be-registered.md).

The RP access certificate format complies with [ETSI TS 119 411-8](https://www.etsi.org/deliver/etsi_ts/119400_119499/11941108/01.01.01_60/ts_11941108v010101p.pdf).
The RP registration certificate format complies with [ETSI TS 119 475](https://www.etsi.org/deliver/etsi_ts/119400_119499/119475/01.02.01_60/ts_119475v010201p.pdf).

The Relying Party Registration Service provides two main functionalities:

+ Register a new Relying Party, issuing the Relying Party Access certificate and keypair in pkcs#12 (P12) format and Registration certificate in JSON Web Tokens (JWT) and CBOR Web Tokens (CWT) format, both signed with an Advanced Electronic Signature (ADES) with the B-B profile .
+ List the certificates issued and enable their revocation.

You can use the Relying Party registration service at https://registry.serviceproviders.eudiw.dev/, or install it locally.

## :heavy_exclamation_mark: Disclaimer

The released software is a initial development release version:

-   The initial development release is an early endeavor reflecting the efforts of a short timeboxed
    period, and by no means can be considered as the final product.
-   The initial development release may be changed substantially over time, might introduce new
    features but also may change or remove existing ones, potentially breaking compatibility with your
    existing code.
-   The initial development release is limited in functional scope.
-   The initial development release may contain errors or design flaws and other problems that could
    cause system or other failures and data loss.
-   The initial development release has reduced security, privacy, availability, and reliability
    standards relative to future releases. This could make the software slower, less reliable, or more
    vulnerable to attacks than mature software.
-   The initial development release is not yet comprehensively documented.
-   Users of the software must perform sufficient engineering and additional testing in order to
    properly evaluate their application and determine whether any of the open-sourced components is
    suitable for use in that application.
-   We strongly recommend not putting this version of the software into production use.
-   Only the latest version of the software will be supported



## Installation

Pre-requisites:

  + Python v. 3.9 or 3.10
  + Flask v. 2.3 or higher

Steps: 

To run the EUDIW Issuer, please follow these simple steps (some of which may have already been completed when installing Flask) for Linux/macOS or Windows.


1. Clone the EUDIW Issuer repository:

    ```shell
    git clone git@github.com:eu-digital-identity-wallet/eudi-srv-web-relyingparty-registration-py.git
    ```


2. Enter the project folder

    ```shell
    cd eudi-srv-web-relyingparty-registration-py
    ```

3. Create .venv to install flask and other libraries

    Windows:
    
    ```shell
    python -m venv .venv 
    ```
    
    Linux:

    ```shell
    python3 -m venv .venv
    ```

4. Activate the environment

    windows:
      
    ```shell
    . .venv\Scripts\Activate
    ```
      
    Linux:
    
    ```shell
    . .venv/bin/activate
    ```
    
5. Install the necessary libraries to run the code

    ```shell
    pip install -r app/requirements.txt
    ```

6. Run the Project

    ```shell
    flask --app app run
    ```

## Run

### 1. Database
     
To create the database use the [app/relying_party_reg.sql](app/relying_party_reg.sql) file. It has been tested with MariaDB version 11.5.
  
The file [app/app_config/database.py](app/app_config/database.py) is used to configure the data needed to connect to the database.



### 2. EJBCA
  
The service needs a connection to an EJBCA (<https://www.ejbca.org/>) instance, in order to issue the certificates.
The configuration file for defining access credentials and the location of the admin's PKCS#12 Keystore file and its corresponding password can be found at [app/app_config/EJBCA_config.py](app/app_config/EJBCA_config.py).

+ clientP12ArchiveFilepath - Path for the .p12 needed for the client certificate authentication
+ clientP12ArchivePassword - .p12 password
+ certificateProfilename - Certificate Profile Name already created in EJBCA
+ endEntityProfileName - End Entity Profile name already created in EJBCA
+ username - End Entity username
+ password - End Entity password

### 3. Status List Service

According to ETSI 119 475 - V1.2.1, the WRPRC must be associated with a Status List to provide information about the WRPRC’s validity.

The WRPRC shall ensure that each WRPRC includes a reference to:
• the status list’s unique identifier (e.g., URI); and
• the position index assigned to that WRPRC within the status list.

To this effect, integration with the [eudi-srv-statuslist-py](https://github.com/eu-digital-identity-wallet/eudi-srv-statuslist-py) service was implemented. 

The connection is made via the **url_statuslist** defined in the file [app/app_config/config.py](app/app_config/config.py)
    
### 4. Initial Page

The initial Page of the Relying Party Registration Service (http://127.0.0.1:5000/ or http://localhost:5000/). 

The http://localhost:5000/guide contains an overview of the standard workflow and endpoints of the Registrar Service.

The http://localhost:5000/apidodcs contains the Swagger documentation.

## Run docker

To start Web Relying Party Registration service a docker compose file, [docker-compose.yml](docker/docker-compose.yml), has been implemented that can be found in `docker` directory.

To start the docker compose environment

```
# From project root directory 
cd docker
docker-compose up -d
```

To stop the docker compose environment

```
# From project root directory 
cd docker
docker-compose down
````

## Configuration

The Web Relying Party Registration application can be configured using the following environment variables:

Variable: `SERVICE_URL`<br>
Description: Application service url

Variable: `TRUSTED_CAS_PATH`<br>
Description: Container path where CA certificates are located for validate vp_token when doing PID login

Variable: `VERIFIER`<br>
Description: Verifier URL

Variable: `LOG_PATH`<br>
Description: Path where log files are saved

Variable: `SCA_SIGNER_URL`<br>
Description: SCA URL 

Variable: `URL_STATUSLIST`<br>
Description: Status List URL

Variable: `STATUSLIST_API_KEY`<br>
Description: Status List API Key

Variable: `WRPRC_CERTIFICATE`<br>
Description: Container path where the WRPRC signing certificate is stored

Variable: `WRPRC_PRIVATE_KEY`<br>
Description: Container path where the private key of the WRPRC signing certificate is stored

Variable: `WRPRC_INTERMEDIATE_CERTIFICATE`<br>
Description: Container path where the intermediate certificate of the WRPRC signing certificate is stored (for certificate chain)

Variable: `DB_HOST`<br>
Description: Database URL

Variable: `DB_PORT`<br>
Description: Port where Database is running

Variable: `DB_USER`<br>
Description: Username of Database user

Variable: `DB_PASSWORD`<br>
Description: Password of Database user

Variable: `DB_NAME`<br>
Description: Name of Database

Variable: `EJBCA_URL`<br>
Description: EJBCA URL

Variable: `CLIENTP12_ARCHIVE_FILEPATH`<br>
Description: Client P12 file to acess EJBCA

Variable: `MANAGEMENT_CA`<br>
Description: EJBCA Management CA

Variable: `CLIENTP12_ARCHIVE_PASSWORD`<br>
Description: Cliente P12 password to acess EJBCA

Variable: `EJBCA_USERNAME`<br>
Description: Username of EJBCA user

Variable: `EJBCA_PASSWORD`<br>
Description: Password of EJBCA user

Variable: `CERTIFICATE_PROFILE_NAME`<br>
Description: Name of the profile defined in the EJBCA application

Variable: `END_ENTITY_PROFILE_NAME`<br>
Description: Name of the End Entity Profile defined in the EJBCA application


## Workflow and Endpoints

This project also provides a simple HTTP-based interface, allowing the service to be accessed through direct HTTP requests instead of a graphical user interface.

To simplify interaction with the available endpoints, the project includes [Swagger API](https://registry.serviceproviders.eudiw.dev/swagger) documentation, which can be accessed through the following route:

```code
/swagger
```

The Swagger interface provides detailed information about all available endpoints, including:

  * Endpoint descriptions
  * Required parameters
  * Request body structures
  * Example requests and responses

This allows developers and users to easily explore and test the API.

For further information on the required workflow and general information about the endpoints, please refer to the [Workflow and Endpoint](workflow_and_endpoints.md) guide.

## How to contribute

We welcome contributions to this project. To ensure that the process is smooth for everyone
involved, follow the guidelines found in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

### License details

Copyright (c) 2024 European Commission

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.