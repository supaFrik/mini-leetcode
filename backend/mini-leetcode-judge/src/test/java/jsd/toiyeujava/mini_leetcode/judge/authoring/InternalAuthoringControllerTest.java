package jsd.toiyeujava.mini_leetcode.judge.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import jsd.toiyeujava.mini_leetcode.judge.console.ConsoleProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

class InternalAuthoringControllerTest {

    private final ConsoleProperties props = new ConsoleProperties();
    private final ReferenceRunService runner = mock(ReferenceRunService.class);
    private final InternalAuthoringController controller =
            new InternalAuthoringController(new GradingConfigValidator(new ObjectMapper()), runner, props);

    @Test
    void rejectsMissingOrWrongToken() {
        props.setInternalToken("secret");
        var body = new InternalAuthoringController.RunRequest(List.of(), List.of(), 1000L, 256);
        assertThat(controller.run(null, body).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.run("nope", body).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.validate("nope", new InternalAuthoringController.ValidateRequest("{}"))
                .getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(runner);
    }

    @Test
    void blankConfiguredTokenRejectsEverything() {
        props.setInternalToken("");
        assertThat(controller.validate("", new InternalAuthoringController.ValidateRequest("{}"))
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void mapsBusyAndBadRequests() {
        props.setInternalToken("secret");
        var body = new InternalAuthoringController.RunRequest(List.of(), List.of(), null, null);
        doThrow(new ReferenceRunService.BusyException()).when(runner).run(any(), any(), anyLong(), anyInt());
        assertThat(controller.run("secret", body).getStatusCode().value()).isEqualTo(503);
        doThrow(new IllegalArgumentException("No inputs to run")).when(runner).run(any(), any(), anyLong(), anyInt());
        assertThat(controller.run("secret", body).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void validateReturnsSummary() {
        props.setInternalToken("secret");
        var response = controller.validate("secret",
                new InternalAuthoringController.ValidateRequest("{\"rules\":[{\"targetClassName\":\"A\"}]}"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).asString().contains("valid=true").contains("ruleCount=1");
    }
}
