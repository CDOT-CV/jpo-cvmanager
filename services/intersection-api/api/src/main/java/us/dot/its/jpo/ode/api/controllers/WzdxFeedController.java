package us.dot.its.jpo.ode.api.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import us.dot.its.jpo.ode.api.services.WzdxService;

@Slf4j
@RestController
@ConditionalOnProperty(name = "enable.api", havingValue = "true", matchIfMissing = false)
@RequestMapping("/wzdx-feed")
public class WzdxFeedController {

    private final WzdxService wzdxService;

    public WzdxFeedController(WzdxService wzdxService) {
        this.wzdxService = wzdxService;
    }

    @GetMapping
    public @ResponseBody ResponseEntity<String> get_request() {
        String wzdxResponse = wzdxService.callWzdxApi();

        return ResponseEntity.ok(wzdxResponse);
    }
}
