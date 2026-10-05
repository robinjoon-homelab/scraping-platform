package architecturefixtures.bad.adapter.inbound.web;

import java.util.List;
import org.jetbrains.exposed.v1.core.ResultRow;

class ExposedResponse {
    ResultRow row;
}

class GenericExposedResponse {
    List<ResultRow> rows;
}
