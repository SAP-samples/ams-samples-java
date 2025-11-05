package customer.ams_cap_bookshop.security;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cloud.security.ams.api.AttributeName;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CdsAttributeProvider implements com.sap.cloud.security.ams.cap.api.CdsAttributeProvider {
    @Override
    public Map<AttributeName, Object> getAttributeInput(CdsEntity target, String eventName) {
        return Map.of(AttributeName.of("foo"), "bar");
    }
}
