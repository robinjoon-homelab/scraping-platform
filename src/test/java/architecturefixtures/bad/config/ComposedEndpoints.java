package architecturefixtures.bad.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseBody;

@RestController
@Retention(RetentionPolicy.RUNTIME)
@interface ApiEndpoint {}

@ApiEndpoint
class ComposedEndpoint {}

@Controller
@ResponseBody
class MvcEndpoint {}
