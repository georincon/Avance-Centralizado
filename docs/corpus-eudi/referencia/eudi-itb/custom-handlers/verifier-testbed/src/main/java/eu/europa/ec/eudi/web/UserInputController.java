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
package eu.europa.ec.eudi.web;

import com.gitb.core.ValueEmbeddingEnumeration;
import com.gitb.tr.TAR;
import com.gitb.tr.TestResultType;
import eu.europa.ec.eudi.gitb.StateManager;
import eu.europa.ec.eudi.gitb.TestBedNotifier;
import eu.europa.ec.eudi.gitb.Utils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple REST controller to allow an easy way of providing a message for the test bed.
 *
 * <p>This implementation acts a sample of how messages could be sent to the test bed. In this case
 * this is done via a simple HTTP GET service that accepts two parameters:
 *
 * <ul>
 *   <li>session: The test session ID. Not providing this will send notifications for all active
 *       sessions.
 *   <li>message: The message to send. Not providing this will consider an empty string.
 * </ul>
 *
 * One of the key points to define when using a messaging service is the approach to match received
 * messages to waiting test bed sessions. In this example a very simple approach is foreseen,
 * expecting the session ID to be passed as a parameter (or be omitted to signal all sessions). A
 * more realistic approach would be as follows:
 *
 * <ol>
 *   <li>The messaging service records as part of the state for each session a property that will
 *       serve to uniquely identify it. This could be a transaction identifier, an endpoint address,
 *       or some other metadata.
 *   <li>The input provided to the messaging service includes the identifier to use for session
 *       matching.
 *   <li>Given such an identifier, the current session state is scanned to find the corresponding
 *       session.
 * </ol>
 *
 * In addition, keep in mind that the communication protocol involved in sending and receiving
 * messages could be anything. In this example we use a HTTP GET request but this could be an email,
 * a SOAP web service call, a polled endpoint or filesystem location; anything that corresponds to
 * the actual messaging needs.
 */
@RestController
public class UserInputController {

  @Autowired private StateManager stateManager = null;
  @Autowired private TestBedNotifier testBedNotifier = null;
  @Autowired private Utils utils = null;

  /**
   * HTTP GET service to receive input for the test bed.
   *
   * <p>Input received here will be provided back to the test bed as a response to its 'receive'
   * step.
   *
   * @param session The test session ID this relates to. Omitting this will consider all active
   *     sessions.
   * @param message The message to send. No message will result in an empty string.
   * @return A text configuration message.
   */
  @RequestMapping(value = "/input", method = RequestMethod.GET)
  public String provideMessage(
      @RequestParam(value = "session", required = false) String session,
      @RequestParam(value = "message", defaultValue = "") String message) {
    List<String> sessionIds = new ArrayList<>();
    if (session == null) {
      // Send message to all current sessions.
      sessionIds.addAll(stateManager.getAllSessions().keySet());
    } else {
      sessionIds.add(session);
    }
    // Input for the test bed is provided by means of a report.
    TAR notificationReport = utils.createReport(TestResultType.SUCCESS);
    // The report can include any properties and with any nesting (by nesting list of map types). In
    // this case we add a simple string.
    notificationReport
        .getContext()
        .getItem()
        .add(
            utils.createAnyContentSimple(
                "messageReceived", message, ValueEmbeddingEnumeration.STRING));
    for (String sessionId : sessionIds) {
      testBedNotifier.notifyTestBed(
          sessionId,
          null,
          (String) stateManager.getSessionInfo(sessionId, StateManager.SessionData.CALLBACK_URL),
          notificationReport);
    }
    return String.format("Sent message [%s] to %s session(s)", message, sessionIds.size());
  }
}
