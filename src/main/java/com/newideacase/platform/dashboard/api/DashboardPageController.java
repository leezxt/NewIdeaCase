package com.newideacase.platform.dashboard.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardPageController {

    @GetMapping({"/dashboard", "/dashboard/"})
    String dashboard() {
        return "forward:/dashboard/index.html";
    }
}
