package br.com.wlabs.hayiz.doc.listener.util;

import okhttp3.FormBody;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HTMLUtil {

    public static FormBody.Builder mapToFormBody(Map<String, String> values) {
        FormBody.Builder formBody = new FormBody.Builder();
        values.forEach((k, v) -> {
            formBody.addEncoded(k, v);
        });
        return formBody;
    }

    public static Map<String, String> formElementToMap(FormElement formElement) {
        Map<String, String> values = new HashMap<>();

        if(Objects.isNull(formElement)) {
            return values;
        }

        for(Element el: formElement.elements()) {
            if (!el.tag().isFormSubmittable()) {
                continue;
            }

            if(el.hasAttr("disabled")) {
                continue;
            }

            String type = el.attr("type");
            if (type.equalsIgnoreCase("button")
                    || type.equalsIgnoreCase("submit")) {
                continue;
            }

            if (type.equalsIgnoreCase("radio")) {
                String checked = el.attr("checked");
                if(!checked.equalsIgnoreCase("checked")) {
                    continue;
                }
            }

            String name = el.attr("name");
            if (name.length() == 0) continue;

            values.put(name, el.val());
        }
        return values;
    }

    public static String urlEncodeUTF8(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(String.format("%s=%s", urlEncodeUTF8(entry.getKey()), urlEncodeUTF8(entry.getValue())
            ));
        }
        return sb.toString();
    }

    public static String urlEncodeUTF8(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
