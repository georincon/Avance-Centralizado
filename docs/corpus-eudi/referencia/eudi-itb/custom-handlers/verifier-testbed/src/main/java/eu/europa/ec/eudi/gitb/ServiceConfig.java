/*
 * Copyright (c) 2025-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.gitb;

import com.gitb.tr.ObjectFactory;
import eu.europa.ec.eudi.issuer.service.IssuerValidationService;
import eu.europa.ec.eudi.qes.service.QesValidationService;
import eu.europa.ec.eudi.verifier.service.VerifierValidationService;
import javax.xml.namespace.QName;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration class responsible for creating the Spring beans required by the service. */
@Configuration
public class ServiceConfig {

  /**
   * The CXF endpoint that will serve messaging service calls.
   *
   * @return The endpoint.
   */
  @Bean
  public EndpointImpl messagingService(
      Bus cxfBus, MessagingServiceImpl messagingServiceImplementation) {
    EndpointImpl endpoint = new EndpointImpl(cxfBus, messagingServiceImplementation);
    endpoint.setServiceName(new QName("http://www.gitb.com/ms/v1/", "MessagingServiceService"));
    endpoint.setEndpointName(new QName("http://www.gitb.com/ms/v1/", "MessagingServicePort"));
    endpoint.publish("/messaging");
    return endpoint;
  }

  /**
   * The CXF endpoint that will serve validation service calls.
   *
   * @return The endpoint.
   */
  @Bean
  public EndpointImpl validationService(
      Bus cxfBus, ValidationServiceImpl validationServiceImplementation) {
    EndpointImpl endpoint = new EndpointImpl(cxfBus, validationServiceImplementation);
    endpoint.setServiceName(new QName("http://www.gitb.com/vs/v1/", "ValidationService"));
    endpoint.setEndpointName(new QName("http://www.gitb.com/vs/v1/", "ValidationServicePort"));
    endpoint.publish("/validation");
    return endpoint;
  }

  /**
   * The CXF endpoint that will serve validation service calls.
   *
   * @return The endpoint.
   */
  @Bean
  public EndpointImpl logsValidationService(
      Bus cxfBus, VerifierValidationService verifierValidationService) {
    EndpointImpl endpoint = new EndpointImpl(cxfBus, verifierValidationService);
    endpoint.setServiceName(new QName("http://www.gitb.com/vs/v1/", "ValidationService"));
    endpoint.setEndpointName(new QName("http://www.gitb.com/vs/v1/", "ValidationServicePort"));
    endpoint.publish("/log/validation");
    return endpoint;
  }

  @Bean
  public EndpointImpl issuanceValidationService(
      Bus cxfBus, IssuerValidationService issuerValidationService) {
    EndpointImpl endpoint = new EndpointImpl(cxfBus, issuerValidationService);
    endpoint.setServiceName(new QName("http://www.gitb.com/vs/v1/", "ValidationService"));
    endpoint.setEndpointName(new QName("http://www.gitb.com/vs/v1/", "ValidationServicePort"));
    endpoint.publish("/log/validation/issuance");
    return endpoint;
  }

  @Bean
  public EndpointImpl rqesValidationService(Bus cxfBus, QesValidationService qesValidationService) {
    EndpointImpl endpoint = new EndpointImpl(cxfBus, qesValidationService);
    endpoint.setServiceName(new QName("http://www.gitb.com/vs/v1/", "ValidationService"));
    endpoint.setEndpointName(new QName("http://www.gitb.com/vs/v1/", "ValidationServicePort"));
    endpoint.publish("/log/validation/qes");
    return endpoint;
  }

  /**
   * The ObjectFactory used to construct GITB classes.
   *
   * @return The factory.
   */
  @Bean
  public ObjectFactory objectFactory() {
    return new ObjectFactory();
  }
}
