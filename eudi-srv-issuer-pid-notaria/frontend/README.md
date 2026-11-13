# EUDIW Issuer Front End

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

**Important!** Before you proceed, please read
the [EUDI Wallet Reference Implementation project description](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)


### Overview

The **EUDIW Issuer Frontend** provides the web interface for interacting with the [EUDIW Issuer Backend](https://github.com/eu-digital-identity-wallet/eudi-srv-web-issuing-eudiw-py).  
It enables users to authenticate, select credentials, and initiate issuance flows in accordance with the [OpenID for Verifiable Credential Issuance (OIDC4VCI)]((https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)) specifications.

This frontend is designed to integrate seamlessly with the EUDIW Issuer services, supporting credential issuance using both `mso_mdoc` and `SD-JWT-VC` formats.


You can use the hosted version at [https://issuer.eudiw.dev/](https://issuer.eudiw.dev/), or run it locally for development.



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


## 1. Installation

Pre-requisites:

+ Python v. 3.9 or 3.10
+ Flask v. 2.3 or higher
+ NPM 10.6.0
+ NodeJS v20.12.2

Click [here](install.md) for detailed installation instructions.


## 2. Run

Click [here](install.md) for detailed instructions.

## 3. Frequently Asked Questions

### A. How to make your local EUDIW Issuer Front-end available on the Internet?

Please see detailed instructions in [install.md](install.md#4-make-your-local-eudiw-issuer-available-on-the-internet-optional).

### B. How can I create a credential offer to issue a credential?

Please see detailed instructions in [api_docs/credential_offer.md](api_docs/credential_offer.md).

### C. Can I test the pre-authorized flow?

Yes. Please see how in [api_docs/pre-authorized.md](api_docs/pre-authorized.md).

### D. Can I run the EUDIW Issuer Front-end end in a Docker container?

Yes. Please see how in [Install Docker](install.md#6-docker).


## How to contribute

We welcome contributions to this project. To ensure that the process is smooth for everyone
involved, follow the guidelines found in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

### License details

Copyright (c) 2023 European Commission

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
