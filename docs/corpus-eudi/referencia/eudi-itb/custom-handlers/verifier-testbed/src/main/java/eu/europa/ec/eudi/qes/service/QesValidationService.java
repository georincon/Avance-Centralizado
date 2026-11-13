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
package eu.europa.ec.eudi.qes.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.gitb.core.AnyContent;
import com.gitb.core.ValueEmbeddingEnumeration;
import com.gitb.tr.TAR;
import com.gitb.tr.TestResultType;
import com.gitb.vs.*;
import com.gitb.vs.Void;
import eu.europa.ec.eudi.gitb.Utils;
import eu.europa.ec.eudi.qes.dto.QesDocumentRetrievalLogsTO;
import eu.europa.ec.eudi.verifier.utils.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QesValidationService implements ValidationService {
  @Autowired private Utils utils;
  @Autowired private Json json;

  private final Logger log = LoggerFactory.getLogger(QesValidationService.class);

  @Override
  public GetModuleDefinitionResponse getModuleDefinition(Void parameters) {
    return new GetModuleDefinitionResponse();
  }

  @Override
  public ValidationResponse validate(ValidateRequest validateRequest) {
    String providedText = utils.getRequiredString(validateRequest.getInput(), "text");

    Optional<String> expectedLog =
        utils.getOptionalString(validateRequest.getInput(), "expectedLog");
    ;

    QesDocumentRetrievalLogsTO providedLogs;
    try {
      providedLogs = json.getReader().readValue(providedText, QesDocumentRetrievalLogsTO.class);
      log.info("Loaded relying party's logs into QesDocumentRetrievalLogsTO Object.");
    } catch (JsonProcessingException e) {
      log.error(
          "Failed to load relying party's logs into  QesDocumentRetrievalLogsTO Object. Exception Message: {}",
          e.getMessage());
      throw new RuntimeException(e);
    }

    TAR report;
    if (providedLogs.successful()) report = utils.createReport(TestResultType.SUCCESS);
    else report = utils.createReport(TestResultType.FAILURE);

    List<QesDocumentRetrievalLogsTO.LogEntry> errors = new ArrayList<>();
    for (QesDocumentRetrievalLogsTO.LogEntry entry : providedLogs.logs()) {
      if (entry.level().equals("ERROR")) {
        errors.add(entry);
      }
    }

    try {
      addToReport("Relying Party's Logs", report, providedLogs.logs());
      if (!errors.isEmpty()) addToReport("Relying Party's Error Logs", report, errors);
      log.info("Added relying party's logs to Report.");
    } catch (JsonProcessingException e) {
      log.error(
          "Failed to add relying party's log to Report. Exception Message: {}", e.getMessage());
      throw new RuntimeException(e);
    }

    ValidationResponse result = new ValidationResponse();
    result.setReport(report);
    return result;
  }

  private void addToReport(String name, TAR report, List<QesDocumentRetrievalLogsTO.LogEntry> logs)
      throws JsonProcessingException {
    AnyContent content = new AnyContent();
    content.setName(name);
    content.setType("application/json");
    content.setEncoding("UTF-8");
    content
        .getItem()
        .add(
            utils.createAnyContentSimple(
                "JSON Data",
                json.getWriter().writeValueAsString(logs),
                ValueEmbeddingEnumeration.STRING));
    report.getContext().getItem().add(content);
  }
}
