package com.bujirun.bujirun.global.config;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

/**
 * 컨트롤러별 담당자를 Swagger Operation summary에 자동으로 붙여준다.
 * 담당자는 git log 기준 해당 컨트롤러의 최다 커밋 작성자로 매핑했다.
 * (전체 컨트롤러에 일괄로 Swagger 어노테이션만 추가한 9f025937a 커밋은 실제 기능 기여가 아니라 집계에서 제외)
 */
@Component
public class OwnerOperationCustomizer implements OperationCustomizer {

    private static final Map<Class<?>, String> CONTROLLER_OWNERS = Map.ofEntries(
            Map.entry(com.bujirun.bujirun.domain.auth.controller.AuthController.class, "성빈"),
            Map.entry(com.bujirun.bujirun.domain.auth.controller.KakaoController.class, "성빈"),
            Map.entry(com.bujirun.bujirun.domain.auth.controller.UserController.class, "윤제승"),
            Map.entry(com.bujirun.bujirun.domain.group.controller.GroupController.class, "윤제승"),
            Map.entry(com.bujirun.bujirun.domain.itinerary.controller.ItineraryController.class, "윤제승"),
            Map.entry(com.bujirun.bujirun.domain.itinerary.generate.controller.BusArrivalController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.itinerary.generate.controller.GroupItineraryController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.itinerary.generate.controller.ItineraryGenerateController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.itinerary.optimize.controller.ItineraryOptimizeController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.itinerary.vote.controller.ItineraryVoteController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.log.controller.TravelLogController.class, "윤제승"),
            Map.entry(com.bujirun.bujirun.domain.upload.controller.UploadController.class, "윤제승"),
            Map.entry(com.bujirun.bujirun.global.controller.HealthController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.bookmark.controller.BookmarkController.class, "윤제승"),
            Map.entry(com.bujirun.bujirun.domain.spot.controller.SpotController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.spot.scheduler.MigrationController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.swipe.controller.SwipeController.class, "유정"),
            Map.entry(com.bujirun.bujirun.domain.visit.controller.VisitController.class, "윤제승"),
            Map.entry(com.bujirun.bujirun.domain.collection.controller.CollectionController.class, "유정")
    );

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        String owner = CONTROLLER_OWNERS.get(handlerMethod.getBeanType());
        if (owner != null) {
            String summary = operation.getSummary();
            operation.setSummary((summary == null || summary.isBlank() ? "" : summary) + " (담당: " + owner + ")");
        }
        return operation;
    }
}
