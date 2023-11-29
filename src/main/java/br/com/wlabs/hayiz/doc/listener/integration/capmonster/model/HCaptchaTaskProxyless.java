package br.com.wlabs.hayiz.doc.listener.integration.capmonster.model;

public class HCaptchaTaskProxyless extends Captcha {

    public HCaptchaTaskProxyless() {
        super();
        params.put("type", "HCaptchaTaskProxyless");
    }

    public void setWebsiteKey(String websiteKey) {
        params.put("websiteKey", websiteKey);
    }

    public void setWebsiteURL(String websiteURL) {
        params.put("websiteURL", websiteURL);
    }

    public void setInvisible(Boolean isInvisible) {
        params.put("isInvisible", isInvisible);
    }

    public void setUserAgent(String userAgent) {
        params.put("userAgent", userAgent);
    }

    public void setCookies(String cookies) {
        params.put("cookies", cookies);
    }

}
