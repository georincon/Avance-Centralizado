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
package eu.europa.ec.eudi.qes.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QesDocumentRetrievalLogsTO(
    @JsonProperty("id") String id,
    @JsonProperty("logs") List<LogEntry> logs,
    @JsonProperty("successful") boolean successful) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LogEntry(
      @JsonProperty("timestamp") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
          LocalDateTime timestamp,
      @JsonProperty("level") String level,
      @JsonProperty("message") String message) {}
}
