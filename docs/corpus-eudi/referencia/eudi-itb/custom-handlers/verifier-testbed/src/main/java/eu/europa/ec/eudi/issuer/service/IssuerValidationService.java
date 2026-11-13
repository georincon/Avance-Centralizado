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
package eu.europa.ec.eudi.issuer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gitb.core.AnyContent;
import com.gitb.core.ValueEmbeddingEnumeration;
import com.gitb.tr.TAR;
import com.gitb.tr.TestResultType;
import com.gitb.vs.*;
import com.gitb.vs.Void;
import eu.europa.ec.eudi.gitb.Utils;
import eu.europa.ec.eudi.issuer.dto.CredentialOfferLogsTO;
import eu.europa.ec.eudi.verifier.utils.Json;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IssuerValidationService implements ValidationService {
  @Autowired private Utils utils;
  @Autowired private Json json;

  private Logger log = LoggerFactory.getLogger(IssuerValidationService.class);

  @Override
  public GetModuleDefinitionResponse getModuleDefinition(Void parameters) {
    return new GetModuleDefinitionResponse();
  }

  @Override
  public ValidationResponse validate(ValidateRequest parameters) {
    log.info(
        "Received 'validate' command from test bed for session [{}]", parameters.getSessionId());

    String providedText = utils.getRequiredString(parameters.getInput(), "text");
    log.info("Retrieved issuer's logs from 'input' text.");

    boolean expectedSuccess = true;
    try {
      expectedSuccess =
          Boolean.parseBoolean(utils.getRequiredString(parameters.getInput(), "expected"));
      log.info("Retrieved 'expected' boolean.");
    } catch (Exception e) {
      log.warn("None 'expected' boolean was received. Exception Message: {}", e.getMessage());
    }

    Optional<String> expectedLog = utils.getOptionalString(parameters.getInput(), "expectedLog");

    CredentialOfferLogsTO providedLogs;
    try {
      providedLogs = json.getReader().readValue(providedText, CredentialOfferLogsTO.class);
      log.info("Loaded issuer's logs into CredentialOfferLogs Object.");
    } catch (JsonProcessingException e) {
      log.error(
          "Failed to load issuer's logs into  CredentialOfferLogs Object. Exception Message: {}",
          e.getMessage());
      throw new RuntimeException(e);
    }

    TAR report;
    if (expectedLog.isPresent()) {
      boolean logFound =
          providedLogs.getLogs().stream()
              .anyMatch(logEntry -> logEntry.contains(expectedLog.get()));
      report = utils.createReport(logFound ? TestResultType.SUCCESS : TestResultType.FAILURE);
    } else if (providedLogs.getSuccessful()) {
      report = utils.createReport(TestResultType.SUCCESS);
    } else if (!expectedSuccess) {
      report = utils.createReport(TestResultType.SUCCESS);
    } else {
      report = utils.createReport(TestResultType.FAILURE);
    }
    log.info("Added test result type to Report.");

    try {
      fromCredentialOfferToJson(providedLogs, report);
      log.info("Created JSON Array from list of issuer's logs.");
      log.info("Added issuer's logs to Report.");
    } catch (JsonProcessingException e) {
      log.error("Failed to add issuer's log to Report. Exception Message: {}", e.getMessage());
      throw new RuntimeException(e);
    }

    ValidationResponse result = new ValidationResponse();
    result.setReport(report);
    return result;
  }

  private static final Pattern LOG_LINE_PATTERN =
      Pattern.compile(
          "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})\\s*\\|\\s*([\\w.]+)\\s*\\|\\s*"
              + "(INFO|WARN|ERROR|DEBUG|TRACE)\\s*\\|\\s*(?:,\\s*)?(.*)$",
          Pattern.DOTALL);

  private void fromCredentialOfferToJson(CredentialOfferLogsTO logs, TAR report)
      throws JsonProcessingException {
    int info_counter_logs = 0;
    int warn_counter_logs = 0;
    int error_counter_logs = 0;

    List<String> errors = new ArrayList<>();
    ArrayNode logsJson = this.json.getReader().createArrayNode();
    for (String logLine : logs.getLogs()) {
      Matcher m = LOG_LINE_PATTERN.matcher(logLine);
      if (!m.matches()) {
        log.warn(
            "Failed to retrieved required information (timestamp, logger name, level) from log {}",
            logLine);
        continue;
      }
      ObjectNode singleLogJson = this.json.getReader().createObjectNode();
      singleLogJson.put("timestamp", m.group(1));
      singleLogJson.put("logger", m.group(2));
      singleLogJson.put("level", m.group(3));
      singleLogJson.put("message", m.group(4));
      singleLogJson.put("full_log", logLine);
      switch (m.group(3)) {
        case "INFO" -> info_counter_logs++;
        case "WARN" -> warn_counter_logs++;
        case "ERROR" -> {
          error_counter_logs++;
          errors.add(logLine);
        }
      }
      logsJson.add(singleLogJson);
    }
    toContentAndAddToReport(logsJson, "Issuer's Logs", report);

    ObjectNode counter = this.json.getReader().createObjectNode();
    counter.put("error_count", error_counter_logs);
    counter.put("warn_count", warn_counter_logs);
    counter.put("info_count", info_counter_logs);
    counter.put("total_count", logs.getCount());
    toContentAndAddToReport(counter, "Issuer's Logs Stats", report);

    if (!errors.isEmpty()) {
      toContentAndAddToReport((Serializable) errors, "Errors", report);
    }
  }

  private void toContentAndAddToReport(Serializable logs, String name, TAR report)
      throws JsonProcessingException {
    AnyContent result = new AnyContent();
    result.setName(name);
    result.setType("application/json");
    result.setEncoding("UTF-8");
    result
        .getItem()
        .add(
            utils.createAnyContentSimple(
                "JSON Data",
                json.getWriter().writeValueAsString(logs),
                ValueEmbeddingEnumeration.STRING));

    report.getContext().getItem().add(result);
  }
}
