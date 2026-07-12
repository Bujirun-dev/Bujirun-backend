package com.bujirun.bujirun.global.config;

import com.bujirun.bujirun.domain.auth.controller.AuthController;
import com.bujirun.bujirun.domain.auth.controller.KakaoController;
import com.bujirun.bujirun.domain.visit.controller.VisitController;
import com.bujirun.bujirun.global.controller.HealthController;
import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnerOperationCustomizerTest {

    private final OwnerOperationCustomizer customizer = new OwnerOperationCustomizer();

    @Test
    void 매핑된_컨트롤러는_summary_뒤에_담당자가_붙는다() {
        Operation operation = new Operation().summary("카카오 로그인");
        HandlerMethod handlerMethod = handlerMethodOf(KakaoController.class);

        Operation result = customizer.customize(operation, handlerMethod);

        assertThat(result.getSummary()).isEqualTo("카카오 로그인 (담당: 성빈)");
    }

    @Test
    void 컨트롤러마다_다른_담당자가_매핑된다() {
        assertThat(customizer.customize(new Operation().summary("s"), handlerMethodOf(AuthController.class)).getSummary())
                .endsWith("(담당: 성빈)");
        assertThat(customizer.customize(new Operation().summary("s"), handlerMethodOf(VisitController.class)).getSummary())
                .endsWith("(담당: 윤제승)");
        assertThat(customizer.customize(new Operation().summary("s"), handlerMethodOf(HealthController.class)).getSummary())
                .endsWith("(담당: 유정)");
    }

    @Test
    void summary가_없어도_예외없이_담당자만_채워진다() {
        Operation operation = new Operation();
        HandlerMethod handlerMethod = handlerMethodOf(KakaoController.class);

        Operation result = customizer.customize(operation, handlerMethod);

        assertThat(result.getSummary()).isEqualTo(" (담당: 성빈)");
    }

    @Test
    void 매핑되지_않은_컨트롤러는_summary가_그대로_유지된다() {
        Operation operation = new Operation().summary("건드리지 않음");
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getBeanType()).thenAnswer(invocation -> Object.class);

        Operation result = customizer.customize(operation, handlerMethod);

        assertThat(result.getSummary()).isEqualTo("건드리지 않음");
    }

    private HandlerMethod handlerMethodOf(Class<?> controllerType) {
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getBeanType()).thenAnswer(invocation -> controllerType);
        return handlerMethod;
    }
}
